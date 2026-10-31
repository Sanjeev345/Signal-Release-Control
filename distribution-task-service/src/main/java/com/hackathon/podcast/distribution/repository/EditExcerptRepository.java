package com.hackathon.podcast.distribution.repository;

import com.hackathon.podcast.distribution.domain.EditExcerpt;
import com.hackathon.podcast.distribution.domain.EditExcerptId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EditExcerptRepository extends JpaRepository<EditExcerpt, EditExcerptId> {
}
