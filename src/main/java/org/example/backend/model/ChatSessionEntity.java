package org.example.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.backend.utils.JsonConverter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "chat_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionEntity {

    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "title")
    private String title = "新对话";

    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "last_accessed")
    @UpdateTimestamp
    private LocalDateTime lastAccessed;

    @Column(name = "message_count")
    private Integer messageCount = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "expiry_time")
    private LocalDateTime expiryTime;

    @Column(name = "metadata", columnDefinition = "json")
    @Convert(converter = JsonConverter.class)
    private Map<String, Object> metadata = new HashMap<>();
}