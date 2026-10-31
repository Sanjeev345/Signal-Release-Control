package com.hackathon.podcast.release;

import com.hackathon.podcast.release.config.ApiProblemWriter;
import com.hackathon.podcast.release.config.SecurityConfig;
import com.hackathon.podcast.release.service.ReleaseService;
import com.hackathon.podcast.release.service.WithdrawalService;
import com.hackathon.podcast.release.web.ReleaseController;
import com.hackathon.podcast.release.web.dto.AuditEntryResponse;
import com.hackathon.podcast.release.web.dto.WithdrawalAcceptedResponse;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReleaseController.class)
@Import({SecurityConfig.class, ApiProblemWriter.class})
@TestPropertySource(properties = {
        "app.security.issuer=http://localhost:8180/realms/podcast-demo",
        "app.security.jwk-set-uri=http://localhost:8180/realms/podcast-demo/protocol/openid-connect/certs"
})
class WithdrawalApiContractTest {

    private static final String GUEST_ID = "22222222-2222-2222-2222-222222222222";
    private static final String RELEASE_ID = "d76025ba-7021-47f0-ab83-4749a581fd61";
    private static final String EXCERPT_ID = "f0f4e4bf-dff7-433c-91be-b8b151c47e42";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ReleaseService releaseService;

    @MockitoBean
    WithdrawalService withdrawalService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void withdrawalReturns202ContractFields() throws Exception {
        UUID eventId = UUID.fromString("1ac7c51a-5f7a-4b0a-9fc9-9ba4bef605f0");
        when(withdrawalService.withdraw(
                eq(UUID.fromString(RELEASE_ID)),
                eq(UUID.fromString(EXCERPT_ID)),
                eq("I no longer consent to this excerpt."),
                eq(GUEST_ID),
                eq("contract-correlation-1")))
                .thenReturn(WithdrawalAcceptedResponse.accepted(
                        eventId, UUID.fromString(RELEASE_ID), UUID.fromString(EXCERPT_ID)));

        mockMvc.perform(post("/api/releases/{releaseId}/scopes/{excerptId}/withdrawals",
                        RELEASE_ID, EXCERPT_ID)
                        .with(jwt().jwt(j -> j.subject(GUEST_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .header("X-Correlation-Id", "contract-correlation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"I no longer consent to this excerpt.\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.releaseId").value(RELEASE_ID))
                .andExpect(jsonPath("$.excerptId").value(EXCERPT_ID))
                .andExpect(jsonPath("$.status").value("WITHDRAWN_ACCEPTED"));

        verify(withdrawalService).withdraw(
                UUID.fromString(RELEASE_ID), UUID.fromString(EXCERPT_ID),
                "I no longer consent to this excerpt.", GUEST_ID, "contract-correlation-1");
    }

    @Test
    void owningGuestCanReadAuditHistory() throws Exception {
        UUID eventId = UUID.fromString("1ac7c51a-5f7a-4b0a-9fc9-9ba4bef605f0");
        when(releaseService.listAudit(UUID.fromString(RELEASE_ID), GUEST_ID, false))
                .thenReturn(List.of(new AuditEntryResponse(
                        UUID.fromString("4f75aae7-4d9e-4a49-a80c-91ebcb75aa3d"),
                        UUID.fromString(RELEASE_ID),
                        UUID.fromString("4ace15d0-2e39-488e-8bc1-4084a08d557a"),
                        UUID.fromString(EXCERPT_ID),
                        "WITHDRAWAL",
                        GUEST_ID,
                        "I no longer consent to this excerpt.",
                        Instant.parse("2026-10-29T12:00:00Z"),
                        eventId,
                        "contract-correlation-1")));

        mockMvc.perform(get("/api/releases/{releaseId}/audit", RELEASE_ID)
                        .with(jwt().jwt(j -> j.subject(GUEST_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("WITHDRAWAL"))
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$[0].correlationId").value("contract-correlation-1"));
    }

    @Test
    void blankReasonReturnsProblemDetails() throws Exception {
        mockMvc.perform(post("/api/releases/{releaseId}/scopes/{excerptId}/withdrawals",
                        RELEASE_ID, EXCERPT_ID)
                        .with(jwt().jwt(j -> j.subject(GUEST_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void producerCannotWithdrawAGuestScope() throws Exception {
        mockMvc.perform(post("/api/releases/{releaseId}/scopes/{excerptId}/withdrawals",
                        RELEASE_ID, EXCERPT_ID)
                        .with(jwt().jwt(j -> j.subject("11111111-1111-1111-1111-111111111111"))
                                .authorities(new SimpleGrantedAuthority("ROLE_podcastProducer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"producer should not be allowed\"}"))
                .andExpect(status().isForbidden());
    }
}
