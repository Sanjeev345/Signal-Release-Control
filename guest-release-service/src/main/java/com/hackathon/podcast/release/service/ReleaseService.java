package com.hackathon.podcast.release.service;

import com.hackathon.podcast.release.domain.ExcerptReference;
import com.hackathon.podcast.release.domain.GuestRelease;
import com.hackathon.podcast.release.domain.ProducedEpisode;
import com.hackathon.podcast.release.domain.ReleaseScope;
import com.hackathon.podcast.release.domain.ScopeType;
import com.hackathon.podcast.release.repository.ExcerptReferenceRepository;
import com.hackathon.podcast.release.repository.GuestReleaseRepository;
import com.hackathon.podcast.release.repository.ProducedEpisodeRepository;
import com.hackathon.podcast.release.repository.ReleaseAuditRepository;
import com.hackathon.podcast.release.repository.ReleaseScopeRepository;
import com.hackathon.podcast.release.web.dto.AuditEntryResponse;
import com.hackathon.podcast.release.web.dto.CreateReleaseRequest;
import com.hackathon.podcast.release.web.dto.ReleaseResponse;
import com.hackathon.podcast.release.web.dto.ScopeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReleaseService {

    private final GuestReleaseRepository releaseRepository;
    private final ReleaseScopeRepository scopeRepository;
    private final ProducedEpisodeRepository producedEpisodeRepository;
    private final ExcerptReferenceRepository excerptReferenceRepository;
    private final ReleaseAuditRepository auditRepository;

    @Transactional
    public ReleaseResponse createRelease(CreateReleaseRequest request, String producerId) {
        requireProducerOwnedEpisode(request.episodeId(), producerId);

        Set<UUID> uniqueExcerpts = new LinkedHashSet<>(request.excerptIds());
        if (uniqueExcerpts.size() != request.excerptIds().size()) {
            throw new BadRequestException("excerptIds must not contain duplicates");
        }

        String guestId = request.guestId().toString();
        for (UUID excerptId : uniqueExcerpts) {
            ExcerptReference reference = excerptReferenceRepository.findById(excerptId)
                    .orElseThrow(() -> new BadRequestException("Unknown excerpt " + excerptId));
            if (!reference.getEpisodeId().equals(request.episodeId())) {
                throw new BadRequestException("Excerpt " + excerptId + " does not belong to episode "
                        + request.episodeId());
            }
            if (!reference.getGuestId().equals(guestId)) {
                throw new BadRequestException("Excerpt " + excerptId + " does not belong to guest " + guestId);
            }
        }

        Instant now = Instant.now();
        GuestRelease release = releaseRepository.save(
                GuestRelease.grant(guestId, request.episodeId(), now));
        ScopeType scopeType = request.scopeType() == null ? ScopeType.PODCAST_DISTRIBUTION : request.scopeType();
        for (UUID excerptId : uniqueExcerpts) {
            scopeRepository.save(ReleaseScope.grant(release, excerptId, scopeType, now));
        }
        return toResponse(release);
    }

    @Transactional(readOnly = true)
    public List<ReleaseResponse> listMyReleases(String guestId) {
        return releaseRepository.findByGuestIdOrderByGrantedAtDesc(guestId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ReleaseResponse> listEpisodeReleases(UUID episodeId, String producerId) {
        requireProducerOwnedEpisode(episodeId, producerId);
        return releaseRepository.findByEpisodeIdOrderByGrantedAtDesc(episodeId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReleaseResponse getRelease(UUID releaseId, String subject, boolean producer) {
        return toResponse(requireReadableRelease(releaseId, subject, producer));
    }

    @Transactional(readOnly = true)
    public List<AuditEntryResponse> listAudit(UUID releaseId, String subject, boolean producer) {
        requireReadableRelease(releaseId, subject, producer);
        return auditRepository.findByReleaseIdOrderByOccurredAtDesc(releaseId).stream()
                .map(AuditEntryResponse::from)
                .toList();
    }

    private GuestRelease requireReadableRelease(UUID releaseId, String subject, boolean producer) {
        GuestRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Release " + releaseId + " was not found"));
        if (release.getGuestId().equals(subject)) {
            return release;
        }
        if (!producer) {
            throw new AccessDeniedException("Release belongs to another guest");
        }
        requireProducerOwnedEpisode(release.getEpisodeId(), subject);
        return release;
    }

    private ReleaseResponse toResponse(GuestRelease release) {
        List<ScopeResponse> scopes = scopeRepository.findByReleaseIdOrderByGrantedAtAsc(release.getId()).stream()
                .map(scope -> {
                    ExcerptReference reference = excerptReferenceRepository.findById(scope.getExcerptId()).orElse(null);
                    return ScopeResponse.from(scope,
                            reference == null ? null : reference.getCode(),
                            reference == null ? null : reference.getStartMs(),
                            reference == null ? null : reference.getEndMs());
                })
                .toList();
        return ReleaseResponse.from(release, scopes);
    }

    private void requireProducerOwnedEpisode(UUID episodeId, String producerId) {
        ProducedEpisode projection = producedEpisodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode " + episodeId + " was not found"));
        if (!projection.getProducerId().equals(producerId)) {
            throw new AccessDeniedException("Episode belongs to another producer");
        }
    }
}
