package org.example.backend.utils;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.example.backend.model.ChatMessageEntity;
import org.example.backend.utils.AiTypeConversion;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MessageConverter {

    public ChatMessageEntity toEntity(ChatMessage chatMessage, String sessionId, int index) {
        return ChatMessageEntity.builder()
                .sessionId(sessionId)
                .messageIndex(index)
                .messageType(getMessageType(chatMessage))
                .role(getRole(chatMessage))
                .content(AiTypeConversion.extractTextFromMessage(chatMessage))
                .contentHash(calculateHash(chatMessage))
                .metadata(extractMetadata(chatMessage))
                .build();
    }

    public ChatMessage toChatMessage(ChatMessageEntity entity) {
        switch (entity.getMessageType()) {
            case USER:
                return UserMessage.from(entity.getContent());
            case AI:
                return AiMessage.from(entity.getContent());
            case SYSTEM:
                return SystemMessage.from(entity.getContent());
            default:
                throw new IllegalArgumentException("Unknown message type: " + entity.getMessageType());
        }
    }

    private ChatMessageEntity.MessageType getMessageType(ChatMessage chatMessage) {
        if (chatMessage instanceof UserMessage) {
            return ChatMessageEntity.MessageType.USER;
        } else if (chatMessage instanceof AiMessage) {
            return ChatMessageEntity.MessageType.AI;
        } else if (chatMessage instanceof SystemMessage) {
            return ChatMessageEntity.MessageType.SYSTEM;
        }
        throw new IllegalArgumentException("Unknown ChatMessage type: " + chatMessage.getClass());
    }

    private String getRole(ChatMessage chatMessage) {
        if (chatMessage instanceof UserMessage) {
            return "user";
        } else if (chatMessage instanceof AiMessage) {
            return "assistant";
        } else if (chatMessage instanceof SystemMessage) {
            return "system";
        }
        return "unknown";
    }

    private String calculateHash(ChatMessage chatMessage) {
        try {
            String content = AiTypeConversion.extractTextFromMessage(chatMessage);
            // 简单的哈希计算，可以使用MD5或其他算法
            return Integer.toHexString(content.hashCode());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> extractMetadata(ChatMessage chatMessage) {
        Map<String, Object> metadata = new HashMap<>();

        if (chatMessage instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) chatMessage;
            metadata.put("type", "USER");
            if (userMessage.name() != null) {
                metadata.put("name", userMessage.name());
            }
        } else if (chatMessage instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) chatMessage;
            metadata.put("type", "AI");
            metadata.put("hasToolExecution", aiMessage.hasToolExecutionRequests());
        } else if (chatMessage instanceof SystemMessage) {
            metadata.put("type", "SYSTEM");
        }

        return metadata;
    }
    /**
     * 从ChatMessage中提取文本内容
     */
    public String extractTextFromMessage(ChatMessage message) {
        System.out.println("提取文本内容："+message);
        return AiTypeConversion.extractTextFromMessage(message);
    }
    public String extractContent(ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.UserMessage) {
            return ((dev.langchain4j.data.message.UserMessage) message).singleText();
        } else if (message instanceof dev.langchain4j.data.message.AiMessage) {
            return ((dev.langchain4j.data.message.AiMessage) message).text();
        } else if (message instanceof dev.langchain4j.data.message.SystemMessage) {
            return ((dev.langchain4j.data.message.SystemMessage) message).text();
        }
        return message.toString();
    }
}