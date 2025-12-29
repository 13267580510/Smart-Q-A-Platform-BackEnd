package org.example.backend.dto;


import dev.langchain4j.data.message.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchSaveMessagesRequest {
    private String sessionId;
    private Long userId;
    private List<ChatMessage> messages;
    private Boolean skipExisting = true;

    public boolean isValid() {
        return sessionId != null && !sessionId.trim().isEmpty()
                && messages != null && !messages.isEmpty();
    }
}