package com.hackathon.podcast.excerpt.web;

import com.hackathon.podcast.excerpt.service.NotificationService;
import com.hackathon.podcast.excerpt.web.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('guest','podcastProducer')")
    public List<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                           @AuthenticationPrincipal Jwt jwt) {
        return notificationService.list(jwt.getSubject(), unreadOnly);
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("hasAnyRole('guest','podcastProducer')")
    public NotificationResponse markRead(@PathVariable UUID notificationId,
                                         @AuthenticationPrincipal Jwt jwt) {
        return notificationService.markRead(notificationId, jwt.getSubject());
    }

    @PostMapping("/read-all")
    @PreAuthorize("hasAnyRole('guest','podcastProducer')")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("updated", notificationService.markAllRead(jwt.getSubject()));
    }
}
