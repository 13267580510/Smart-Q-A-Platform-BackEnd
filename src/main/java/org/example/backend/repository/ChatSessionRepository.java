package org.example.backend.repository;
import org.example.backend.model.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {

    List<ChatSessionEntity> findByUserId(Long userId);

    List<ChatSessionEntity> findByUserIdOrderByLastAccessedDesc(Long userId);

    List<ChatSessionEntity> findByExpiryTimeBefore(LocalDateTime time);

    Optional<ChatSessionEntity> findBySessionIdAndUserId(String sessionId, Long userId);
}