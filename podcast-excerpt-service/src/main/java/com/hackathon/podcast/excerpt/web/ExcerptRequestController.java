package com.hackathon.podcast.excerpt.web;

import com.hackathon.podcast.excerpt.domain.ExcerptRequestStatus;
import com.hackathon.podcast.excerpt.service.ExcerptRequestService;
import com.hackathon.podcast.excerpt.web.dto.ExcerptRequestResponse;
import com.hackathon.podcast.excerpt.web.dto.RejectExcerptRequest;
import com.hackathon.podcast.excerpt.web.dto.SubmitExcerptRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/excerpts/requests")
@RequiredArgsConstructor
public class ExcerptRequestController {

    private final ExcerptRequestService excerptRequestService;

    @PostMapping
    @PreAuthorize("hasRole('guest')")
    public ResponseEntity<ExcerptRequestResponse> submit(@Valid @RequestBody SubmitExcerptRequest request,
                                                         @AuthenticationPrincipal Jwt jwt) {
        ExcerptRequestResponse response = excerptRequestService.submit(request, jwt.getSubject());
        return ResponseEntity.created(URI.create("/api/excerpts/requests/" + response.id())).body(response);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('guest')")
    public List<ExcerptRequestResponse> myRequests(
            @RequestParam(required = false) ExcerptRequestStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        return excerptRequestService.listForGuest(jwt.getSubject(), status);
    }

    @GetMapping
    @PreAuthorize("hasRole('podcastProducer')")
    public List<ExcerptRequestResponse> producerRequests(
            @RequestParam(required = false) ExcerptRequestStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        return excerptRequestService.listForProducer(jwt.getSubject(), status);
    }

    @GetMapping("/{requestId}")
    @PreAuthorize("hasAnyRole('guest','podcastProducer')")
    public ExcerptRequestResponse get(@PathVariable UUID requestId,
                                      @AuthenticationPrincipal Jwt jwt,
                                      Authentication authentication) {
        boolean producer = isProducer(authentication);
        return excerptRequestService.get(requestId, jwt.getSubject(), producer);
    }

    @PostMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('podcastProducer')")
    public ExcerptRequestResponse approve(@PathVariable UUID requestId,
                                          @AuthenticationPrincipal Jwt jwt) {
        return excerptRequestService.approve(requestId, jwt.getSubject());
    }

    @PostMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('podcastProducer')")
    public ExcerptRequestResponse reject(@PathVariable UUID requestId,
                                         @Valid @RequestBody RejectExcerptRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return excerptRequestService.reject(requestId, request.reason(), jwt.getSubject());
    }

    private static boolean isProducer(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_podcastProducer"));
    }
}
