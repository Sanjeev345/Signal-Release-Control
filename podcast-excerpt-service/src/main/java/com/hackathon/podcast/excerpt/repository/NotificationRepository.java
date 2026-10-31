package com.hackathon.podcast.excerpt.repository;

import com.hackathon.podcast.excerpt.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(String recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, String recipientId);

    boolean existsBySourceEventId(UUID sourceEventId);

    @Modifying
    @Query("update Notification n set n.read = true where n.recipientId = :recipientId and n.read = false")
    int markAllRead(@Param("recipientId") String recipientId);
}
