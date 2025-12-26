package org.example.backend.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.example.backend.service.ai.memory.RedisChatMemoryStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {

    @Bean
    public ChatMemory chatMemory(RedisChatMemoryStore redisChatMemoryStore) {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)  // 保留最近20条消息
                .chatMemoryStore(redisChatMemoryStore)  // 使用你的Redis存储
                .build();
    }
}