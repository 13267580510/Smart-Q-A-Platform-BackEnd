package org.example.backend.service.ai.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import  org.example.backend.service.ai.memory.RedisChatMemoryStore;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class RedisChatMemoryManager {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired  // 添加这个注解，让Spring注入RedisChatMemoryStore
    private RedisChatMemoryStore redisChatMemoryStore;

    // Redis键前缀
    private static final String CHAT_MEMORY_KEY_PREFIX = "chat:memory:";
    private static final String CHAT_MESSAGES_KEY_PREFIX = "chat:messages:";

    // 过期时间配置
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    // 内存配置
    private static final int DEFAULT_MAX_MESSAGES = 20;

    /**
     * 获取或创建聊天内存
     * @param memoryId 内存ID（会话ID）
     * @return ChatMemory实例
     */
    public ChatMemory getChatMemory(String memoryId) {
        // 生成新的内存ID（如果需要）
        if (memoryId == null || memoryId.equals("default")) {
            memoryId = generateMemoryId();
        }

        // 延长Redis中聊天内存的过期时间
        String memoryKey = CHAT_MEMORY_KEY_PREFIX + memoryId;
        redisTemplate.expire(memoryKey, DEFAULT_TTL);

        // 延长消息存储的过期时间
        String messagesKey = CHAT_MESSAGES_KEY_PREFIX + memoryId;
        redisTemplate.expire(messagesKey, DEFAULT_TTL);

        // 创建ChatMemory并配置Redis存储
        return MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(DEFAULT_MAX_MESSAGES)
                .build();
    }

    /**
     * 清理聊天内存（删除所有相关数据）
     * @param memoryId 内存ID（会话ID）
     */
    public void clearChatMemory(String memoryId) {
        if (memoryId == null) {
            return;
        }

        // 删除聊天内存相关的所有Redis键
        String memoryKey = CHAT_MEMORY_KEY_PREFIX + memoryId;
        String messagesKey = CHAT_MESSAGES_KEY_PREFIX + memoryId;

        redisTemplate.delete(memoryKey);
        redisTemplate.delete(messagesKey);

        System.out.println("清理聊天内存: " + memoryId);
    }

    /**
     * 删除消息（clearChatMemory的别名，为了兼容性）
     * @param memoryId 内存ID（会话ID）
     */
    public void deleteMessages(String memoryId) {
        clearChatMemory(memoryId);
    }


    /**
     * 检查聊天内存是否存在
     * @param memoryId 内存ID（会话ID）
     * @return 是否存在
     */
    public boolean exists(String memoryId) {
        if (memoryId == null) {
            return false;
        }

        String memoryKey = CHAT_MEMORY_KEY_PREFIX + memoryId;
        String messagesKey = CHAT_MESSAGES_KEY_PREFIX + memoryId;

        Boolean hasMemory = redisTemplate.hasKey(memoryKey);
        Boolean hasMessages = redisTemplate.hasKey(messagesKey);

        return Boolean.TRUE.equals(hasMemory) || Boolean.TRUE.equals(hasMessages);
    }

    /**
     * 更新聊天内存的过期时间
     * @param memoryId 内存ID（会话ID）
     */
    public void refreshExpiry(String memoryId) {
        if (memoryId == null) {
            return;
        }

        String memoryKey = CHAT_MEMORY_KEY_PREFIX + memoryId;
        String messagesKey = CHAT_MESSAGES_KEY_PREFIX + memoryId;

        // 延长过期时间
        if (Boolean.TRUE.equals(redisTemplate.hasKey(memoryKey))) {
            redisTemplate.expire(memoryKey, DEFAULT_TTL);
        }

        if (Boolean.TRUE.equals(redisTemplate.hasKey(messagesKey))) {
            redisTemplate.expire(messagesKey, DEFAULT_TTL);
        }
    }

    /**
     * 生成新的内存ID
     * @return 生成的内存ID
     */
    private String generateMemoryId() {
        return "memory_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }
}