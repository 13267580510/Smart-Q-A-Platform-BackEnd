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
public class ChatMessageDTO {
    private String sessionId;
    private Integer index;
    private String type;
    private String role;
    private String content;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
}