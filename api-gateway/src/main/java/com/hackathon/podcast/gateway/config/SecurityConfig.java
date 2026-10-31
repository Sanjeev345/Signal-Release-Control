package com.hackathon.podcast.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${app.security.jwk-set-uri}") String jwkSetUri,
            @Value("${app.security.issuer}") String issuer,
            @Value("${app.security.audience}") String audience) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(jwtValidator(issuer, audience));
        return decoder;
    }

    @Bean
    public OAuth2TokenValidator<Jwt> jwtValidator(
            @Value("${app.security.issuer}") String issuer,
            @Value("${app.security.audience}") String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator(audience));
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String expectedAudience) {
        return token -> {
            List<String> audiences = token.getAudience();
            return audiences != null && audiences.contains(expectedAudience)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
                            "Required audience '" + expectedAudience + "' is missing", null));
        };
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        Converter<Jwt, AbstractAuthenticationToken> roles =
                new JwtRoleAuthenticationConverter();

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers(HttpMethod.POST,
                                "/api/excerpts/episodes",
                                "/api/excerpts/episodes/*/excerpts",
                                "/api/releases").hasRole("podcastProducer")
                        .pathMatchers(HttpMethod.POST,
                                "/api/excerpts/requests").hasRole("guest")
                        .pathMatchers(HttpMethod.POST,
                                "/api/excerpts/requests/*/approve",
                                "/api/excerpts/requests/*/reject").hasRole("podcastProducer")
                        .pathMatchers(HttpMethod.GET,
                                "/api/excerpts/episodes",
                                "/api/releases",
                                "/api/distribution/edits").hasRole("podcastProducer")
                        .pathMatchers(HttpMethod.PATCH,
                                "/api/distribution/tasks/**").hasRole("podcastProducer")
                        .pathMatchers(HttpMethod.POST,
                                "/api/releases/*/scopes/*/withdrawals").hasRole("guest")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(
                                new ReactiveJwtAuthenticationConverterAdapter(roles)))
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of(CorrelationIdWebFilter.HEADER));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, ex) -> writeProblem(exchange, HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED", "Authentication required");
    }

    private ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, ex) -> writeProblem(exchange, HttpStatus.FORBIDDEN,
                "FORBIDDEN", "You do not have access to this resource");
    }

    private Mono<Void> writeProblem(ServerWebExchange exchange, HttpStatus status,
                                    String code, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("code", code);
        body.put("traceId", exchange.getAttributes().get(CorrelationIdWebFilter.ATTRIBUTE));
        body.put("timestamp", Instant.now());
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception ex) {
            return Mono.error(ex);
        }
    }
}
