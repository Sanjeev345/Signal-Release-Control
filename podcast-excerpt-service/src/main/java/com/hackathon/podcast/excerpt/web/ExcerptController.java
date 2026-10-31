package com.hackathon.podcast.excerpt.web;

import com.hackathon.podcast.excerpt.service.ExcerptService;
import com.hackathon.podcast.excerpt.web.dto.CreateExcerptRequest;
import com.hackathon.podcast.excerpt.web.dto.ExcerptResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/excerpts/episodes/{episodeId}/excerpts")
@RequiredArgsConstructor
public class ExcerptController {

    private final ExcerptService excerptService;

    @PostMapping
    @PreAuthorize("hasRole('podcastProducer')")
    public ResponseEntity<ExcerptResponse> createExcerpt(@PathVariable UUID episodeId,
                                                         @Valid @RequestBody CreateExcerptRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        ExcerptResponse response = excerptService.createExcerpt(episodeId, request, jwt.getSubject());
        return ResponseEntity.created(URI.create(
                "/api/excerpts/episodes/" + episodeId + "/excerpts/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('podcastProducer','guest')")
    public List<ExcerptResponse> listExcerpts(@PathVariable UUID episodeId,
                                              @AuthenticationPrincipal Jwt jwt,
                                              Authentication authentication) {
        boolean producer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_podcastProducer"));
        return excerptService.listExcerpts(episodeId, jwt.getSubject(), producer);
    }
}
