package com.hackathon.podcast.excerpt;

import com.hackathon.podcast.excerpt.config.ApiProblemWriter;
import com.hackathon.podcast.excerpt.config.SecurityConfig;
import com.hackathon.podcast.excerpt.service.ExcerptRequestService;
import com.hackathon.podcast.excerpt.web.ExcerptRequestController;
import com.hackathon.podcast.excerpt.web.dto.ExcerptRequestResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExcerptRequestController.class)
@Import({SecurityConfig.class, ApiProblemWriter.class})
@TestPropertySource(properties = {
        "app.security.issuer=http://localhost:8180/realms/podcast-demo",
        "app.security.jwk-set-uri=http://localhost:8180/realms/podcast-demo/protocol/openid-connect/certs"
})
class ExcerptRequestSecurityTest {

    private static final String EPISODE_ID = "2a3f078d-471e-46bb-a4b7-23ad33300960";
    private static final String REQUEST_ID = "7f3e2b1a-9c8d-4e6f-8a1b-2c3d4e5f6071";
    private static final String PRODUCER_ID = "11111111-1111-1111-1111-111111111111";
    private static final String GUEST_ID = "22222222-2222-2222-2222-222222222222";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ExcerptRequestService excerptRequestService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void guestCanSubmitARequestAndIdentityComesFromTheToken() throws Exception {
        when(excerptRequestService.submit(any(), eq(GUEST_ID))).thenReturn(response("PENDING"));

        mockMvc.perform(post("/api/excerpts/requests")
                        .with(jwt().jwt(j -> j.subject(GUEST_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"episodeId":"%s","title":"Trust moment","description":"Why trust matters",
                                 "startMs":1000,"endMs":2000}
                                """.formatted(EPISODE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.guestId").value(GUEST_ID));
    }

    @Test
    void guestCannotApproveRequests() throws Exception {
        mockMvc.perform(post("/api/excerpts/requests/{id}/approve", REQUEST_ID)
                        .with(jwt().jwt(j -> j.subject(GUEST_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestCannotReadTheProducerQueue() throws Exception {
        mockMvc.perform(get("/api/excerpts/requests")
                        .with(jwt().jwt(j -> j.subject(GUEST_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void producerCanListPendingRequests() throws Exception {
        when(excerptRequestService.listForProducer(eq(PRODUCER_ID), any()))
                .thenReturn(List.of(response("PENDING")));

        mockMvc.perform(get("/api/excerpts/requests").param("status", "PENDING")
                        .with(jwt().jwt(j -> j.subject(PRODUCER_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_podcastProducer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void unauthenticatedSubmissionIsRejected() throws Exception {
        mockMvc.perform(post("/api/excerpts/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTitleIsAValidationFailure() throws Exception {
        mockMvc.perform(post("/api/excerpts/requests")
                        .with(jwt().jwt(j -> j.subject(GUEST_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"episodeId\":\"%s\",\"startMs\":0,\"endMs\":1000}"
                                .formatted(EPISODE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ExcerptRequestResponse response(String status) {
        Instant now = Instant.now();
        return new ExcerptRequestResponse(
                UUID.fromString(REQUEST_ID),
                UUID.fromString(EPISODE_ID),
                "Designing for Trust",
                GUEST_ID,
                "Trust moment",
                "Why trust matters",
                1000L,
                2000L,
                status,
                null,
                null,
                null,
                null,
                now,
                now);
    }
}
