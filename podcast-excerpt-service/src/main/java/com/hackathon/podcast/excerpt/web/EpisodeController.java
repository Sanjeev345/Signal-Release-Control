package com.hackathon.podcast.excerpt.web;

import com.hackathon.podcast.excerpt.service.ExcerptService;
import com.hackathon.podcast.excerpt.web.dto.CreateEpisodeRequest;
import com.hackathon.podcast.excerpt.web.dto.EpisodeResponse;
import com.hackathon.podcast.excerpt.web.dto.EpisodeSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/excerpts")
@RequiredArgsConstructor
public class EpisodeController {

    private final ExcerptService excerptService;

    @PostMapping("/episodes")
    @PreAuthorize("hasRole('podcastProducer')")
    public ResponseEntity<EpisodeResponse> createEpisode(@Valid @RequestBody CreateEpisodeRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        EpisodeResponse response = excerptService.createEpisode(request, jwt.getSubject());
        return ResponseEntity.created(URI.create("/api/excerpts/episodes/" + response.id())).body(response);
    }

    @GetMapping("/episodes")
    @PreAuthorize("hasRole('podcastProducer')")
    public List<EpisodeResponse> listEpisodes(@AuthenticationPrincipal Jwt jwt) {
        return excerptService.listEpisodes(jwt.getSubject());
    }

    @GetMapping("/episodes/available")
    @PreAuthorize("hasAnyRole('podcastProducer','guest')")
    public List<EpisodeSummaryResponse> listAvailableEpisodes() {
        return excerptService.listAvailableEpisodes();
    }

    @GetMapping("/episodes/{episodeId}")
    @PreAuthorize("hasRole('podcastProducer')")
    public EpisodeResponse getEpisode(@PathVariable UUID episodeId, @AuthenticationPrincipal Jwt jwt) {
        return excerptService.getEpisode(episodeId, jwt.getSubject());
    }
}
