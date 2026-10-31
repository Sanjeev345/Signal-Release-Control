package com.hackathon.podcast.release.web;

import com.hackathon.podcast.release.service.ReleaseService;
import com.hackathon.podcast.release.service.WithdrawalService;
import com.hackathon.podcast.release.web.dto.AuditEntryResponse;
import com.hackathon.podcast.release.web.dto.CreateReleaseRequest;
import com.hackathon.podcast.release.web.dto.ReleaseResponse;
import com.hackathon.podcast.release.web.dto.WithdrawalAcceptedResponse;
import com.hackathon.podcast.release.web.dto.WithdrawalRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/releases")
@RequiredArgsConstructor
public class ReleaseController {

    private final ReleaseService releaseService;
    private final WithdrawalService withdrawalService;

    @PostMapping
    @PreAuthorize("hasRole('podcastProducer')")
    public ResponseEntity<ReleaseResponse> createRelease(@Valid @RequestBody CreateReleaseRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        ReleaseResponse response = releaseService.createRelease(request, jwt.getSubject());
        return ResponseEntity.created(URI.create("/api/releases/" + response.id())).body(response);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('guest')")
    public List<ReleaseResponse> myReleases(@AuthenticationPrincipal Jwt jwt) {
        return releaseService.listMyReleases(jwt.getSubject());
    }

    @GetMapping
    @PreAuthorize("hasRole('podcastProducer')")
    public List<ReleaseResponse> episodeReleases(@RequestParam UUID episodeId,
                                                 @AuthenticationPrincipal Jwt jwt) {
        return releaseService.listEpisodeReleases(episodeId, jwt.getSubject());
    }

    @GetMapping("/{releaseId}")
    @PreAuthorize("hasAnyRole('guest','podcastProducer')")
    public ReleaseResponse getRelease(@PathVariable UUID releaseId,
                                      @AuthenticationPrincipal Jwt jwt,
                                      Authentication authentication) {
        boolean producer = hasRole(authentication, "ROLE_podcastProducer");
        return releaseService.getRelease(releaseId, jwt.getSubject(), producer);
    }

    @GetMapping("/{releaseId}/audit")
    @PreAuthorize("hasAnyRole('guest','podcastProducer')")
    public List<AuditEntryResponse> audit(@PathVariable UUID releaseId,
                                          @AuthenticationPrincipal Jwt jwt,
                                          Authentication authentication) {
        boolean producer = hasRole(authentication, "ROLE_podcastProducer");
        return releaseService.listAudit(releaseId, jwt.getSubject(), producer);
    }

    @PostMapping("/{releaseId}/scopes/{excerptId}/withdrawals")
    @PreAuthorize("hasRole('guest')")
    public ResponseEntity<WithdrawalAcceptedResponse> withdraw(
            @PathVariable UUID releaseId,
            @PathVariable UUID excerptId,
            @Valid @RequestBody WithdrawalRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        String resolvedCorrelationId = correlationId == null || correlationId.isBlank()
                ? MDC.get("correlationId")
                : correlationId;
        WithdrawalAcceptedResponse response = withdrawalService.withdraw(
                releaseId, excerptId, request.reason(), jwt.getSubject(), resolvedCorrelationId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }
}
