package com.hackathon.podcast.excerpt.service;

import com.hackathon.podcast.excerpt.domain.Episode;
import com.hackathon.podcast.excerpt.domain.PodcastExcerpt;
import com.hackathon.podcast.excerpt.repository.EpisodeRepository;
import com.hackathon.podcast.excerpt.repository.PodcastExcerptRepository;
import com.hackathon.podcast.excerpt.web.dto.CreateEpisodeRequest;
import com.hackathon.podcast.excerpt.web.dto.CreateExcerptRequest;
import com.hackathon.podcast.excerpt.web.dto.EpisodeResponse;
import com.hackathon.podcast.excerpt.web.dto.EpisodeSummaryResponse;
import com.hackathon.podcast.excerpt.web.dto.ExcerptResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExcerptService {

    private final EpisodeRepository episodeRepository;
    private final PodcastExcerptRepository excerptRepository;

    @Transactional
    public EpisodeResponse createEpisode(CreateEpisodeRequest request, String producerId) {
        Episode episode = Episode.create(
                "ep-" + shortId(),
                request.title().trim(),
                producerId,
                request.publishedAt());
        return EpisodeResponse.from(episodeRepository.save(episode));
    }

    @Transactional(readOnly = true)
    public List<EpisodeResponse> listEpisodes(String producerId) {
        return episodeRepository.findByProducerIdOrderByCreatedAtDesc(producerId)
                .stream().map(EpisodeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EpisodeResponse getEpisode(UUID episodeId, String producerId) {
        return EpisodeResponse.from(requireOwnedEpisode(episodeId, producerId));
    }

    /**
     * Published episodes any authenticated user may reference when submitting a request.
     * Only catalogue metadata is exposed, never producer internals.
     */
    @Transactional(readOnly = true)
    public List<EpisodeSummaryResponse> listAvailableEpisodes() {
        return episodeRepository
                .findByPublishedAtIsNullOrPublishedAtLessThanEqualOrderByPublishedAtDesc(Instant.now())
                .stream().map(EpisodeSummaryResponse::from).toList();
    }

    @Transactional
    public ExcerptResponse createExcerpt(UUID episodeId, CreateExcerptRequest request, String producerId) {
        Episode episode = requireOwnedEpisode(episodeId, producerId);
        if (request.endMs() <= request.startMs()) {
            throw new BadRequestException("endMs must be greater than startMs");
        }
        PodcastExcerpt excerpt = PodcastExcerpt.create(
                episode,
                "ex-" + shortId(),
                request.guestId().trim(),
                request.startMs(),
                request.endMs(),
                request.transcript());
        return ExcerptResponse.from(excerptRepository.save(excerpt));
    }

    /**
     * Producers see every excerpt on their own episode. A guest sees only excerpts whose
     * guestId matches their JWT subject, even when querying someone else's episode.
     */
    @Transactional(readOnly = true)
    public List<ExcerptResponse> listExcerpts(UUID episodeId, String subject, boolean producer) {
        if (producer) {
            requireOwnedEpisode(episodeId, subject);
            return excerptRepository.findByEpisodeIdOrderByStartMsAsc(episodeId)
                    .stream().map(ExcerptResponse::from).toList();
        }
        return excerptRepository.findByEpisodeIdAndGuestIdOrderByStartMsAsc(episodeId, subject)
                .stream().map(ExcerptResponse::from).toList();
    }

    private Episode requireOwnedEpisode(UUID episodeId, String producerId) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Episode " + episodeId + " was not found"));
        if (!episode.getProducerId().equals(producerId)) {
            throw new AccessDeniedException("Episode belongs to another producer");
        }
        return episode;
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
