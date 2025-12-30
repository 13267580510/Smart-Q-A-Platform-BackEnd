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

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public String getContent() {
        return content;
    }

    public String getRole() {
        return role;
    }

    public String getType() {
        return type;
    }

    public Integer getIndex() {
        return index;
    }

    public String getSessionId() {
        return sessionId;
    }

    private LocalDateTime timestamp;


}