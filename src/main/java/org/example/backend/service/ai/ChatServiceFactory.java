package org.example.backend.service.ai;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.memory.chat.*;  // 这个应该是对的
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.*;
import org.example.backend.service.ai.memory.RedisChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class ChatServiceFactory {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private ContentRetriever contentRetriever;

    @Autowired
    private StreamingChatModel streamingChatModel;

    @Autowired  // 添加这个
    private RedisChatMemoryStore redisChatMemoryStore;

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object memoryId) {
                String id = memoryId != null ? memoryId.toString() : "default";

                if (id.equals("default")) {
                    // 生成新的内存ID
                    id = "memory_" + System.currentTimeMillis() + "_" +
                            java.util.UUID.randomUUID().toString().substring(0, 8);
                }

                System.out.println("ChatMemoryProvider: 为会话 " + id + " 创建 ChatMemory");

                // 使用你的 RedisChatMemoryStore
                return MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(20)  // 保留最近20条消息
                        .chatMemoryStore(redisChatMemoryStore)  // 关键：使用Redis存储
                        .build();
            }
        };
    }

    @Bean
    public ChatService chatService(ChatMemoryProvider chatMemoryProvider) {
        ChatService chatService = AiServices.builder(ChatService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .contentRetriever(contentRetriever)
                .build();

        return chatService;
    }
}