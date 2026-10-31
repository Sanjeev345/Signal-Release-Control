package com.hackathon.podcast.excerpt.service;

import com.hackathon.podcast.excerpt.domain.Episode;
import com.hackathon.podcast.excerpt.domain.ExcerptRequest;
import com.hackathon.podcast.excerpt.domain.ExcerptRequestStatus;
import com.hackathon.podcast.excerpt.domain.NotificationEventType;
import com.hackathon.podcast.excerpt.repository.EpisodeRepository;
import com.hackathon.podcast.excerpt.repository.ExcerptRequestRepository;
import com.hackathon.podcast.excerpt.web.dto.CreateExcerptRequest;
import com.hackathon.podcast.excerpt.web.dto.ExcerptRequestResponse;
import com.hackathon.podcast.excerpt.web.dto.ExcerptResponse;
import com.hackathon.podcast.excerpt.web.dto.SubmitExcerptRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExcerptRequestService {

    private final EpisodeRepository episodeRepository;
    private final ExcerptRequestRepository requestRepository;
    private final ExcerptService excerptService;
    private final NotificationService notificationService;

    /**
     * Submitting a request never creates consent or an excerpt. Approval is a separate,
     * producer-only decision that goes through the existing excerpt creation flow.
     */
    @Transactional
    public ExcerptRequestResponse submit(SubmitExcerptRequest request, String guestId) {
        Episode episode = episodeRepository.findById(request.episodeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Episode " + request.episodeId() + " was not found"));
        if (episode.getPublishedAt() != null && episode.getPublishedAt().isAfter(Instant.now())) {
            throw new BadRequestException("Episode is not published yet");
        }
        if (request.endMs() <= request.startMs()) {
            throw new BadRequestException("endMs must be greater than startMs");
        }

        ExcerptRequest entity = ExcerptRequest.pending(
                episode,
                guestId,
                request.title().trim(),
                trimToNull(request.description()),
                request.startMs(),
                request.endMs());
        ExcerptRequest saved = requestRepository.save(entity);

        notificationService.notify(
                episode.getProducerId(),
                "New excerpt request \"" + saved.getTitle() + "\" for episode \""
                        + episode.getTitle() + "\" from guest " + guestId,
                NotificationEventType.REQUEST_SUBMITTED,
                saved.getId());
        return ExcerptRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ExcerptRequestResponse> listForGuest(String guestId, ExcerptRequestStatus status) {
        List<ExcerptRequest> requests = status == null
                ? requestRepository.findByGuestIdOrderByCreatedAtDesc(guestId)
                : requestRepository.findByGuestIdAndStatusOrderByCreatedAtDesc(guestId, status);
        return requests.stream().map(ExcerptRequestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ExcerptRequestResponse> listForProducer(String producerId, ExcerptRequestStatus status) {
        List<ExcerptRequest> requests = status == null
                ? requestRepository.findByEpisodeProducerIdOrderByCreatedAtDesc(producerId)
                : requestRepository.findByEpisodeProducerIdAndStatusOrderByCreatedAtDesc(producerId, status);
        return requests.stream().map(ExcerptRequestResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ExcerptRequestResponse get(UUID requestId, String subject, boolean producer) {
        ExcerptRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Excerpt request " + requestId + " was not found"));
        if (producer) {
            requireOwnedEpisode(request, subject);
        } else if (!request.getGuestId().equals(subject)) {
            throw new AccessDeniedException("Request belongs to another guest");
        }
        return ExcerptRequestResponse.from(request);
    }

    @Transactional
    public ExcerptRequestResponse approve(UUID requestId, String producerId) {
        ExcerptRequest request = lockPendingRequest(requestId, producerId);
        Episode episode = request.getEpisode();

        ExcerptResponse excerpt = excerptService.createExcerpt(
                episode.getId(),
                new CreateExcerptRequest(
                        request.getGuestId(),
                        request.getStartMs(),
                        request.getEndMs(),
                        composeTranscript(request)),
                producerId);

        request.approve(producerId, excerpt.id(), Instant.now());
        requestRepository.save(request);

        notificationService.notify(
                request.getGuestId(),
                "Your excerpt request \"" + request.getTitle() + "\" was approved.",
                NotificationEventType.REQUEST_APPROVED,
                request.getId());
        return ExcerptRequestResponse.from(request);
    }

    @Transactional
    public ExcerptRequestResponse reject(UUID requestId, String reason, String producerId) {
        ExcerptRequest request = lockPendingRequest(requestId, producerId);
        String cleanReason = trimToNull(reason);
        if (cleanReason != null && cleanReason.length() > 2000) {
            throw new BadRequestException("Rejection reason must be at most 2000 characters");
        }

        request.reject(producerId, cleanReason, Instant.now());
        requestRepository.save(request);

        String message = "Your excerpt request \"" + request.getTitle() + "\" was rejected.";
        if (cleanReason != null) {
            message = message + " Reason: " + cleanReason;
        }
        notificationService.notify(
                request.getGuestId(), message, NotificationEventType.REQUEST_REJECTED, request.getId());
        return ExcerptRequestResponse.from(request);
    }

    private ExcerptRequest lockPendingRequest(UUID requestId, String producerId) {
        ExcerptRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Excerpt request " + requestId + " was not found"));
        requireOwnedEpisode(request, producerId);
        if (request.getStatus() != ExcerptRequestStatus.PENDING) {
            throw new ConflictException(
                    "Excerpt request " + requestId + " was already " + request.getStatus());
        }
        return request;
    }

    private void requireOwnedEpisode(ExcerptRequest request, String producerId) {
        if (!request.getEpisode().getProducerId().equals(producerId)) {
            throw new AccessDeniedException("Episode belongs to another producer");
        }
    }

    private static String composeTranscript(ExcerptRequest request) {
        String description = request.getDescription();
        if (description == null || description.isBlank()) {
            return request.getTitle();
        }
        return request.getTitle() + "\n\n" + description;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
