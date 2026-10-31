package com.hackathon.podcast.distribution;

import com.hackathon.podcast.distribution.domain.DistributedEdit;
import com.hackathon.podcast.distribution.domain.DistributionTask;
import com.hackathon.podcast.distribution.repository.DistributedEditRepository;
import com.hackathon.podcast.distribution.repository.DistributionTaskRepository;
import com.hackathon.podcast.distribution.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskConstraintTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    DistributedEditRepository editRepository;

    @Autowired
    DistributionTaskRepository taskRepository;

    @Autowired
    ProcessedEventRepository processedEventRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void taskIsUniquePerEventAndEdit() {
        DistributedEdit edit = editRepository.saveAndFlush(DistributedEdit.create(
                "edit-test", UUID.randomUUID(), "YouTube", "ref-1", Instant.now()));
        UUID eventId = UUID.randomUUID();
        taskRepository.saveAndFlush(DistributionTask.openRemoval(edit.getId(), UUID.randomUUID(), eventId, Instant.now()));

        DistributionTask duplicate = DistributionTask.openRemoval(
                edit.getId(), UUID.randomUUID(), eventId, Instant.now());

        assertThatThrownBy(() -> taskRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void processedEventIdIsUnique() {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO processed_event (event_id, event_type, processed_at) VALUES (?, ?, now())",
                eventId, "GuestExcerptWithdrawn");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO processed_event (event_id, event_type, processed_at) VALUES (?, ?, now())",
                eventId, "GuestExcerptWithdrawn"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void taskRequiresExistingEdit() {
        DistributionTask orphan = DistributionTask.openRemoval(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertThatThrownBy(() -> taskRepository.saveAndFlush(orphan))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
