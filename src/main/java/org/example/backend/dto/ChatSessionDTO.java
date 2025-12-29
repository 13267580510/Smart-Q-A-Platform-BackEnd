package org.example.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionDTO {
    private String sessionId;
    private String title;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime lastAccessed;
    private Integer messageCount;
    private LocalDateTime expiryTime;
    private Boolean isValid;
    private Map<String, Object> metadata;
}