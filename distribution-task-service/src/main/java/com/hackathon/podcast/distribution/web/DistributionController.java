package com.hackathon.podcast.distribution.web;

import com.hackathon.podcast.distribution.domain.TaskStatus;
import com.hackathon.podcast.distribution.service.DistributionTaskService;
import com.hackathon.podcast.distribution.web.dto.EditResponse;
import com.hackathon.podcast.distribution.web.dto.TaskResponse;
import com.hackathon.podcast.distribution.web.dto.UpdateTaskRequest;
import com.hackathon.podcast.distribution.web.dto.UpdateTaskStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/distribution")
@RequiredArgsConstructor
public class DistributionController {

    private final DistributionTaskService distributionTaskService;

    @GetMapping("/edits")
    @PreAuthorize("hasRole('podcastProducer')")
    public List<EditResponse> edits(@RequestParam UUID episodeId,
                                    @AuthenticationPrincipal Jwt jwt) {
        return distributionTaskService.listEdits(episodeId, jwt.getSubject());
    }

    /**
     * With an excerptId this returns that excerpt's tasks to its owner guest or the owning
     * producer. Without one it is a producer-only view across owned episodes.
     */
    @GetMapping("/tasks")
    @PreAuthorize("hasAnyRole('guest','podcastProducer')")
    public List<TaskResponse> tasks(@RequestParam(required = false) UUID excerptId,
                                    @RequestParam(required = false) UUID episodeId,
                                    @RequestParam(required = false) String provider,
                                    @RequestParam(required = false) TaskStatus status,
                                    @AuthenticationPrincipal Jwt jwt,
                                    Authentication authentication) {
        boolean producer = isProducer(authentication);
        if (excerptId != null) {
            return distributionTaskService.listTasks(excerptId, jwt.getSubject(), producer);
        }
        if (!producer) {
            throw new AccessDeniedException("Only producers can list tasks across episodes");
        }
        return distributionTaskService.listTasksForProducer(jwt.getSubject(), episodeId, provider, status);
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('guest','podcastProducer')")
    public TaskResponse task(@PathVariable UUID taskId,
                             @AuthenticationPrincipal Jwt jwt,
                             Authentication authentication) {
        return distributionTaskService.getTask(taskId, jwt.getSubject(), isProducer(authentication));
    }

    @PatchMapping("/tasks/{taskId}")
    @PreAuthorize("hasRole('podcastProducer')")
    public TaskResponse updateTask(@PathVariable UUID taskId,
                                   @Valid @RequestBody UpdateTaskRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return distributionTaskService.updateTask(
                taskId, request.status(), request.notes(), request.evidenceUrl(), jwt.getSubject());
    }

    @PatchMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasRole('podcastProducer')")
    public TaskResponse updateTaskStatus(@PathVariable UUID taskId,
                                         @Valid @RequestBody UpdateTaskStatusRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return distributionTaskService.updateTaskStatus(taskId, request.status(), jwt.getSubject());
    }

    private static boolean isProducer(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_podcastProducer"));
    }
}
