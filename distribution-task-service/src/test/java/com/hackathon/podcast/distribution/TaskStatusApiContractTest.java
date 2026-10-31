package com.hackathon.podcast.distribution;

import com.hackathon.podcast.distribution.config.ApiProblemWriter;
import com.hackathon.podcast.distribution.config.SecurityConfig;
import com.hackathon.podcast.distribution.domain.TaskStatus;
import com.hackathon.podcast.distribution.service.DistributionTaskService;
import com.hackathon.podcast.distribution.web.DistributionController;
import com.hackathon.podcast.distribution.web.dto.TaskResponse;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DistributionController.class)
@Import({SecurityConfig.class, ApiProblemWriter.class})
@TestPropertySource(properties = {
        "app.security.issuer=http://localhost:8180/realms/podcast-demo",
        "app.security.jwk-set-uri=http://localhost:8180/realms/podcast-demo/protocol/openid-connect/certs"
})
class TaskStatusApiContractTest {

    private static final String TASK_ID = "8b7f0f22-2a5e-4a94-9e6e-f1e3c7782f10";
    private static final String PRODUCER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    DistributionTaskService distributionTaskService;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void producerCanAdvanceTaskStatus() throws Exception {
        UUID taskId = UUID.fromString(TASK_ID);
        Instant completedAt = Instant.parse("2026-10-29T12:30:00Z");
        when(distributionTaskService.updateTaskStatus(eq(taskId), eq(TaskStatus.DONE), eq(PRODUCER_ID)))
                .thenReturn(new TaskResponse(
                        taskId,
                        UUID.fromString("e0000000-0000-4000-8000-000000000001"),
                        "edit-youtube-001",
                        "YouTube",
                        UUID.fromString("f0f4e4bf-dff7-433c-91be-b8b151c47e42"),
                        UUID.fromString("1ac7c51a-5f7a-4b0a-9fc9-9ba4bef605f0"),
                        "EXCERPT_REMOVAL",
                        "DONE",
                        "HIGH",
                        Instant.parse("2026-10-29T12:00:00Z"),
                        completedAt,
                        completedAt,
                        "Removed the clip; evidence attached",
                        "https://evidence.example.com/youtube/removal-001",
                        PRODUCER_ID));

        mockMvc.perform(patch("/api/distribution/tasks/{taskId}/status", TASK_ID)
                        .with(jwt().jwt(j -> j.subject(PRODUCER_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_podcastProducer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").value("2026-10-29T12:30:00Z"));
    }

    @Test
    void producerCanRecordCompletionNotesAndEvidence() throws Exception {
        UUID taskId = UUID.fromString(TASK_ID);
        Instant completedAt = Instant.parse("2026-10-29T12:30:00Z");
        when(distributionTaskService.updateTask(eq(taskId), eq(TaskStatus.DONE),
                eq("Removed the clip"), eq("https://evidence.example.com/removal-001"), eq(PRODUCER_ID)))
                .thenReturn(new TaskResponse(
                        taskId,
                        UUID.fromString("e0000000-0000-4000-8000-000000000001"),
                        "edit-youtube-001",
                        "YouTube",
                        UUID.fromString("f0f4e4bf-dff7-433c-91be-b8b151c47e42"),
                        UUID.fromString("1ac7c51a-5f7a-4b0a-9fc9-9ba4bef605f0"),
                        "EXCERPT_REMOVAL",
                        "DONE",
                        "HIGH",
                        Instant.parse("2026-10-29T12:00:00Z"),
                        completedAt,
                        completedAt,
                        "Removed the clip",
                        "https://evidence.example.com/removal-001",
                        PRODUCER_ID));

        mockMvc.perform(patch("/api/distribution/tasks/{taskId}", TASK_ID)
                        .with(jwt().jwt(j -> j.subject(PRODUCER_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_podcastProducer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DONE","notes":"Removed the clip",
                                 "evidenceUrl":"https://evidence.example.com/removal-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Removed the clip"))
                .andExpect(jsonPath("$.evidenceUrl").value("https://evidence.example.com/removal-001"))
                .andExpect(jsonPath("$.updatedBy").value(PRODUCER_ID));
    }

    @Test
    void guestCannotRecordTaskMetadata() throws Exception {
        mockMvc.perform(patch("/api/distribution/tasks/{taskId}", TASK_ID)
                        .with(jwt().jwt(j -> j.subject("22222222-2222-2222-2222-222222222222"))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"guest tampering\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestCannotAdvanceTaskStatus() throws Exception {
        mockMvc.perform(patch("/api/distribution/tasks/{taskId}/status", TASK_ID)
                        .with(jwt().jwt(j -> j.subject("22222222-2222-2222-2222-222222222222"))
                                .authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingStatusIsValidationFailure() throws Exception {
        mockMvc.perform(patch("/api/distribution/tasks/{taskId}/status", TASK_ID)
                        .with(jwt().jwt(j -> j.subject(PRODUCER_ID))
                                .authorities(new SimpleGrantedAuthority("ROLE_podcastProducer")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
