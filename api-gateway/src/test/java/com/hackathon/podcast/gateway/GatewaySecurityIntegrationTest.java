package com.hackathon.podcast.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Import(GatewaySecurityIntegrationTest.TestSupport.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class GatewaySecurityIntegrationTest {

    private static final String ISSUER = "http://localhost:8180/realms/podcast-demo";
    private static final String GUEST_ID = "22222222-2222-2222-2222-222222222222";
    private static final String PRODUCER_ID = "11111111-1111-1111-1111-111111111111";

    private static final RSAKey RSA_KEY = generateKey();
    private static final HttpServer STUB = startStub();

    @LocalServerPort
    int port;

    WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @Test
    void missingTokenReturns401ProblemDetails() {
        webTestClient.get().uri("/api/releases/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.traceId").exists();
    }

    @Test
    void guestTokenIsForwardedToDiscoveredRoute() {
        webTestClient.get().uri("/api/releases/me")
                .headers(headers -> headers.setBearerAuth(token(GUEST_ID, "guest")))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value("X-Correlation-Id", value -> org.assertj.core.api.Assertions
                        .assertThat(value).isNotBlank())
                .expectBody()
                .jsonPath("$.gateway").isEqualTo("stubbed");
    }

    @Test
    void guestGets403OnProducerOnlyEndpointAtGateway() {
        webTestClient.post().uri("/api/excerpts/episodes")
                .headers(headers -> headers.setBearerAuth(token(GUEST_ID, "guest")))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"title\":\"Ava tries producer-only\"}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    @Test
    void tokenWithoutRequiredAudienceIsRejectedWith401() {
        webTestClient.get().uri("/api/releases/me")
                .headers(headers -> headers.setBearerAuth(token(GUEST_ID, "guest", false)))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    void producerCanReachProducerOnlyRoute() {
        webTestClient.get().uri("/api/releases/me")
                .headers(headers -> headers.setBearerAuth(token(PRODUCER_ID, "podcastProducer")))
                .exchange()
                .expectStatus().isOk();
    }

    @TestConfiguration
    static class TestSupport {

        @Bean
        @Primary
        ReactiveJwtDecoder testJwtDecoder(OAuth2TokenValidator<Jwt> jwtValidator) throws Exception {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
                    .withPublicKey(RSA_KEY.toRSAPublicKey())
                    .build();
            decoder.setJwtValidator(jwtValidator);
            return decoder;
        }

        @Bean
        RouteLocator stubReleaseRoutes() {
            Route route = Route.async()
                    .id("releases-stub")
                    .uri("http://localhost:" + STUB.getAddress().getPort())
                    .predicate(exchange -> exchange.getRequest().getPath().value()
                            .startsWith("/api/releases/"))
                    .order(-100)
                    .build();
            return () -> Flux.just(route);
        }
    }

    private static String token(String subject, String role) {
        return token(subject, role, true);
    }

    private static String token(String subject, String role, boolean withAudience) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(ISSUER)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                    .claim("realm_access", Map.of("roles", List.of(role)));
            if (withAudience) {
                claims.audience(List.of("podcast-api"));
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
            jwt.sign(new RSASSASigner(RSA_KEY));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not sign test JWT", ex);
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("gateway-test").generate();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate RSA key", ex);
        }
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                byte[] body = "{\"gateway\":\"stubbed\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start gateway stub", ex);
        }
    }
}
