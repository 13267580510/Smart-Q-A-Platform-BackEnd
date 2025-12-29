package org.example.backend.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheKeyConfig {

    // ==================== 聊天会话相关 ====================

    /**
     * 会话信息缓存键
     * 完整键: chat:session:{sessionId}
     */
    public static final String CHAT_SESSION_KEY = "chat:session:";

    /**
     * 用户会话列表缓存键
     * 完整键: chat:user:sessions:{userId}
     */
    public static final String USER_SESSIONS_KEY = "chat:user:sessions:";

    // ==================== 聊天消息相关 ====================

    /**
     * 聊天消息列表缓存键
     * 完整键: chat:messages:{sessionId}
     */
    public static final String CHAT_MESSAGES_KEY = "chat:messages:";

    /**
     * 聊天内存缓存键
     * 完整键: chat:memory:{memoryId}
     */
    public static final String CHAT_MEMORY_KEY = "chat:memory:";

    // ==================== 构建完整键的方法 ====================

    public static String buildSessionKey(String sessionId) {
        return CHAT_SESSION_KEY + sessionId;
    }

    public static String buildUserSessionsKey(String userId) {
        return USER_SESSIONS_KEY + userId;
    }

    public static String buildMessagesKey(String sessionId) {
        return CHAT_MESSAGES_KEY + sessionId;
    }

    public static String buildMemoryKey(String memoryId) {
        return CHAT_MEMORY_KEY + memoryId;
    }
}