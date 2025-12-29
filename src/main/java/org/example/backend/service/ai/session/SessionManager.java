package org.example.backend.service.ai.session;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.config.CacheKeyConfig;
import org.example.backend.model.ChatSessionEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.example.backend.config.FastJson2RedisSerializer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class SessionManager {

    @Getter
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 使用CacheKeyConfig统一管理键前缀
    private static final Duration DEFAULT_EXPIRY = Duration.ofHours(24);
    private static final int MAX_SESSIONS_PER_USER = 100;

    // 内存中的会话缓存（热数据缓存）
    @Getter
    private final Map<String, ChatSession> sessionCache = new ConcurrentHashMap<>();

    // 用户会话映射：userId -> sessionIds（内存缓存）
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    /**
     * 获取或创建会话（关联用户）
     */
    public synchronized String getOrCreateSession(String sessionId, String userId) {
        log.debug("开始执行getOrCreateSession, sessionId={}, userId={}", sessionId, userId);

        if (sessionId == null || sessionId.equals("default")) {
            sessionId = generateSessionId();
            log.debug("生成新sessionId: {}", sessionId);
        }

        // 使用统一键获取缓存
        String sessionKey = CacheKeyConfig.buildSessionKey(sessionId);
        log.debug("构建sessionKey: {}", sessionKey);

        // 先检查内存缓存
        ChatSession cachedSession = sessionCache.get(sessionId);
        if (cachedSession != null) {
            // 更新最后访问时间
            cachedSession.setLastAccessed(LocalDateTime.now());
            cachedSession.setExpiryTime(LocalDateTime.now().plus(DEFAULT_EXPIRY));

            // 如果userId不同，更新userId
            if (userId != null && !userId.equals(cachedSession.getUserId())) {
                // 从旧的用户会话列表中移除
                if (cachedSession.getUserId() != null) {
                    removeSessionFromUser(cachedSession.getUserId(), sessionId);
                }
                // 更新userId并添加到新用户
                cachedSession.setUserId(userId);
                addSessionToUser(userId, sessionId);
            }

            // 同步到Redis
            saveToRedis(sessionId, cachedSession);

            // 如果不是匿名用户，添加到用户会话列表
            if (userId != null && !"anonymous".equals(userId)) {
                addSessionToUser(userId, sessionId);
            }

            log.debug("从缓存获取会话: sessionId={}, userId={}", sessionId, userId);
            return sessionId;
        }

        // 改用getSessionInfo方法获取（自动处理类型转换）
        ChatSession session = getSessionInfo(sessionId);

        if (session == null) {
            // 创建新会话
            session = new ChatSession(sessionId, userId);
            log.info("创建新会话: sessionId={}, userId={}", sessionId, userId);
        } else {
            // 更新现有会话的userId
            session.setUserId(userId);
            log.debug("从Redis加载会话: sessionId={}, userId={}", sessionId, userId);
        }

        session.setLastAccessed(LocalDateTime.now());
        session.setExpiryTime(LocalDateTime.now().plus(DEFAULT_EXPIRY));

        // 保存到Redis
        saveToRedis(sessionId, session);

        // 更新内存缓存
        sessionCache.put(sessionId, session);

        // 添加到用户会话列表（如果不是匿名用户）
        if (userId != null && !"anonymous".equals(userId)) {
            addSessionToUser(userId, sessionId);
        }

        return sessionId;
    }

    /**
     * 添加会话到用户列表
     */
    private void addSessionToUser(String userId, String sessionId) {
        if (userId == null || "anonymous".equals(userId)) {
            return;
        }

        String userKey = CacheKeyConfig.buildUserSessionsKey(userId);

        try {
            // 从Redis获取用户会话列表
            @SuppressWarnings("unchecked")
            Set<String> sessionIds = (Set<String>) redisTemplate.opsForValue().get(userKey);
            if (sessionIds == null) {
                sessionIds = new LinkedHashSet<>();
            }

            // 添加新会话到列表开头（保持最近访问的在前）
            Set<String> newSessionIds = new LinkedHashSet<>();
            newSessionIds.add(sessionId);
            newSessionIds.addAll(sessionIds);

            // 限制每个用户的会话数量
            if (newSessionIds.size() > MAX_SESSIONS_PER_USER) {
                // 移除最旧的会话（LinkedHashSet保持插入顺序，最早的在最后）
                Iterator<String> iterator = newSessionIds.iterator();
                List<String> toRemove = new ArrayList<>();
                int count = 0;
                while (iterator.hasNext()) {
                    String sid = iterator.next();
                    if (count >= MAX_SESSIONS_PER_USER) {
                        toRemove.add(sid);
                    }
                    count++;
                }
                newSessionIds.removeAll(toRemove);
            }

            // 保存回Redis
            redisTemplate.opsForValue().set(userKey, newSessionIds, DEFAULT_EXPIRY);

            // 更新内存缓存
            userSessions.put(userId, newSessionIds);

            log.debug("添加会话到用户列表: userId={}, sessionId={}", userId, sessionId);
        } catch (Exception e) {
            log.error("添加会话到用户列表失败: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    /**
     * 获取用户的所有会话
     */
    public List<ChatSession> getUserSessions(String userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        try {
            // 从内存缓存获取
            Set<String> sessionIds = userSessions.get(userId);
            if (sessionIds == null) {
                // 从Redis获取（使用统一键）
                String userKey = CacheKeyConfig.buildUserSessionsKey(userId);
                @SuppressWarnings("unchecked")
                Set<String> redisSessionIds = (Set<String>) redisTemplate.opsForValue().get(userKey);
                if (redisSessionIds == null) {
                    return Collections.emptyList();
                }
                sessionIds = redisSessionIds;
                userSessions.put(userId, sessionIds);
            }

            // 获取每个会话的详细信息
            List<ChatSession> sessions = new ArrayList<>();
            for (String sessionId : sessionIds) {
                try {
                    ChatSession session = getSessionInfo(sessionId);
                    if (session != null && session.getUserId() != null && session.getUserId().equals(userId)) {
                        sessions.add(session);
                    }
                } catch (Exception e) {
                    log.warn("获取会话信息失败: sessionId={}, userId={}", sessionId, userId, e);
                }
            }

            // 按最后访问时间排序（最新的在前）
            sessions.sort((s1, s2) -> s2.getLastAccessed().compareTo(s1.getLastAccessed()));

            log.debug("获取用户会话列表: userId={}, 数量={}", userId, sessions.size());
            return sessions;
        } catch (Exception e) {
            log.error("获取用户会话列表失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从用户会话列表中移除会话
     */
    public void removeSessionFromUser(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }

        try {
            // 从Redis获取用户会话列表（使用统一键）
            String userKey = CacheKeyConfig.buildUserSessionsKey(userId);
            @SuppressWarnings("unchecked")
            Set<String> sessionIds = (Set<String>) redisTemplate.opsForValue().get(userKey);

            if (sessionIds != null) {
                sessionIds.remove(sessionId);
                redisTemplate.opsForValue().set(userKey, sessionIds, DEFAULT_EXPIRY);
            }

            // 更新内存缓存
            Set<String> cachedSessionIds = userSessions.get(userId);
            if (cachedSessionIds != null) {
                cachedSessionIds.remove(sessionId);
            }

            log.debug("从用户会话列表移除: userId={}, sessionId={}", userId, sessionId);
        } catch (Exception e) {
            log.error("从用户会话列表移除失败: userId={}, sessionId={}", userId, sessionId, e);
        }
    }

    /**
     * 检查会话是否有效
     */
    public boolean isValidSession(String sessionId) {
        if (sessionId == null) {
            return false;
        }

        // 先检查内存缓存
        ChatSession cachedSession = sessionCache.get(sessionId);
        if (cachedSession != null) {
            if (LocalDateTime.now().isAfter(cachedSession.getExpiryTime())) {
                // 缓存过期，清理
                sessionCache.remove(sessionId);
                redisTemplate.delete(CacheKeyConfig.buildSessionKey(sessionId));
                return false;
            }
            // 更新最后访问时间
            cachedSession.setLastAccessed(LocalDateTime.now());
            return true;
        }

        // 从Redis检查（使用统一键）
        String key = CacheKeyConfig.buildSessionKey(sessionId);
        ChatSession session = getSessionInfo(sessionId);

        if (session == null) {
            return false;
        }

        // 检查是否过期
        if (LocalDateTime.now().isAfter(session.getExpiryTime())) {
            // 会话已过期，清理
            redisTemplate.delete(key);

            // 从用户会话列表中移除
            if (session.getUserId() != null) {
                removeSessionFromUser(session.getUserId(), sessionId);
            }

            return false;
        }

        // 更新最后访问时间
        session.setLastAccessed(LocalDateTime.now());
        session.setExpiryTime(LocalDateTime.now().plus(DEFAULT_EXPIRY));

        // 保存回Redis
        saveToRedis(sessionId, session);

        // 更新内存缓存
        sessionCache.put(sessionId, session);

        return true;
    }

    /**
     * 删除会话
     */
    public void removeSession(String sessionId) {
        if (sessionId == null) {
            return;
        }

        try {
            // 获取会话信息以获取userId
            ChatSession session = getSessionInfo(sessionId);

            // 从内存缓存删除
            sessionCache.remove(sessionId);

            // 从Redis删除（使用统一键）
            String key = CacheKeyConfig.buildSessionKey(sessionId);
            redisTemplate.delete(key);

            // 从用户会话列表中移除
            if (session != null && session.getUserId() != null) {
                removeSessionFromUser(session.getUserId(), sessionId);
            }

            log.info("删除会话: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("删除会话失败: sessionId={}", sessionId, e);
        }
    }


    // SessionManager 中核心方法：获取会话信息
    public ChatSession getSessionInfo(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        // 1. 优先查内存缓存
        ChatSession cachedSession = sessionCache.get(sessionId);
        if (cachedSession != null) {
            return cachedSession;
        }

        // 2. 查Redis（捕获序列化异常，避免崩溃）
        String key = CacheKeyConfig.buildSessionKey(sessionId);
        Object sessionObj = null;
        try {
            sessionObj = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("从Redis读取会话失败（序列化异常）: sessionId={}", sessionId, e);
            // 清理损坏的Redis数据
            redisTemplate.delete(key);
            return null;
        }

        // 3. 类型转换逻辑（优先级：ChatSession > ChatSessionEntity > JSON字符串）
        try {
            if (sessionObj == null) {
                return null;
            }

            // 3.1 直接是ChatSession类型（最优）
            if (sessionObj instanceof ChatSession) {
                ChatSession session = (ChatSession) sessionObj;
                sessionCache.put(sessionId, session);
                return session;
            }

            // 3.2 是ChatSessionEntity类型（转换为ChatSession）
            if (sessionObj instanceof ChatSessionEntity) {
                ChatSessionEntity entity = (ChatSessionEntity) sessionObj;
                ChatSession session = convertFromEntity(entity);
                // 同步更新Redis为ChatSession类型（避免后续重复转换）
                saveToRedis(sessionId, session);
                sessionCache.put(sessionId, session);
                return session;
            }

            // 3.3 兼容JSON字符串（旧数据兜底）
            if (sessionObj instanceof String) {
                try {
                    // 尝试解析为ChatSession
                    ChatSession session = com.alibaba.fastjson2.JSON.parseObject(
                            (String) sessionObj,
                            ChatSession.class,
                            FastJson2RedisSerializer.AUTO_TYPE_FILTER,
                            com.alibaba.fastjson2.JSONReader.Feature.SupportAutoType
                    );
                    saveToRedis(sessionId, session);
                    sessionCache.put(sessionId, session);
                    return session;
                } catch (Exception e) {
                    // 解析失败则尝试解析为Entity
                    ChatSessionEntity entity = com.alibaba.fastjson2.JSON.parseObject(
                            (String) sessionObj,
                            ChatSessionEntity.class,
                            FastJson2RedisSerializer.AUTO_TYPE_FILTER,
                            com.alibaba.fastjson2.JSONReader.Feature.SupportAutoType
                    );
                    ChatSession session = convertFromEntity(entity);
                    saveToRedis(sessionId, session);
                    sessionCache.put(sessionId, session);
                    return session;
                }
            }

            // 未知类型：记录日志 + 清理Redis
            log.warn("未知的会话数据类型: sessionId={}, type={}", sessionId, sessionObj.getClass().getName());
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("会话反序列化失败: sessionId={}", sessionId, e);
            // 兜底：删除损坏的Redis数据
            redisTemplate.delete(key);
        }

        return null;
    }
    // 修复convertFromEntity方法（确保字段映射完整）
    private ChatSession convertFromEntity(ChatSessionEntity entity) {
        if (entity == null) {
            return null;
        }
        ChatSession session = new ChatSession(
                entity.getSessionId() != null ? entity.getSessionId() : "",
                entity.getUserId() != null ? String.valueOf(entity.getUserId()) : "anonymous"
        );
        session.setCreatedAt(entity.getCreatedAt() != null ? entity.getCreatedAt() : LocalDateTime.now());
        session.setLastAccessed(entity.getLastAccessed() != null ? entity.getLastAccessed() : LocalDateTime.now());
        session.setExpiryTime(entity.getExpiryTime() != null ? entity.getExpiryTime() : LocalDateTime.now().plusHours(24));
        session.setMessageCount(entity.getMessageCount() != null ? entity.getMessageCount() : 0);
        session.setTitle(entity.getTitle() != null && !entity.getTitle().trim().isEmpty() ? entity.getTitle().trim() : "新对话");
        return session;
    }

    /**
     * 获取用户活跃会话数量
     */
    public long getUserSessionCount(String userId) {
        List<ChatSession> sessions = getUserSessions(userId);
        return sessions.stream()
                .filter(session -> isValidSession(session.getSessionId()))
                .count();
    }

    /**
     * 清理用户过期会话
     */
    public void cleanupUserExpiredSessions(String userId) {
        List<ChatSession> sessions = getUserSessions(userId);
        log.info("清理用户过期会话: userId={}, 总会话数={}", userId, sessions.size());

        int removedCount = 0;
        for (ChatSession session : sessions) {
            if (!isValidSession(session.getSessionId())) {
                removeSession(session.getSessionId());
                removedCount++;
            }
        }

        if (removedCount > 0) {
            log.info("清理完成: userId={}, 清理了{}个过期会话", userId, removedCount);
        }
    }

    /**
     * 增加消息计数
     */
    public void incrementMessageCount(String sessionId) {
        ChatSession session = getSessionInfo(sessionId);
        if (session != null) {
            session.incrementMessageCount();
            session.setLastAccessed(LocalDateTime.now());
            session.setExpiryTime(LocalDateTime.now().plus(DEFAULT_EXPIRY));

            saveToRedis(sessionId, session);
            sessionCache.put(sessionId, session);

            log.debug("增加消息计数: sessionId={}, count={}", sessionId, session.getMessageCount());
        }
    }

    /**
     * 获取活跃会话数量
     */
    public long getActiveSessionCount() {
        return sessionCache.size();
    }

    /**
     * 清理所有过期会话
     */
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        AtomicInteger removedCount = new AtomicInteger();

        sessionCache.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue().getExpiryTime())) {
                String sessionId = entry.getKey();
                ChatSession session = entry.getValue();

                // 从Redis删除（使用统一键）
                redisTemplate.delete(CacheKeyConfig.buildSessionKey(sessionId));

                // 从用户会话列表移除
                if (session.getUserId() != null) {
                    removeSessionFromUser(session.getUserId(), sessionId);
                }

                removedCount.getAndIncrement();
                return true;
            }
            return false;
        });

        if (removedCount.get() > 0) {
            log.info("清理了{}个过期会话", removedCount);
        }
    }

    // SessionManager 中私有方法：保存到Redis
    private void saveToRedis(String sessionId, ChatSession session) {
        try {
            String key = CacheKeyConfig.buildSessionKey(sessionId);
            // 直接使用RedisTemplate的setValue（已配置FastJSON2序列化器）
            redisTemplate.opsForValue().set(key, session, DEFAULT_EXPIRY);
            log.debug("会话保存到Redis成功: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("保存会话到Redis失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8);
    }

    @Data
    public static class ChatSession {
        private final String sessionId;
        private String userId;
        private LocalDateTime createdAt;
        private LocalDateTime lastAccessed;
        private LocalDateTime expiryTime;
        private int messageCount;
        private String title;

        public ChatSession(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.createdAt = LocalDateTime.now();
            this.lastAccessed = LocalDateTime.now();
            this.expiryTime = LocalDateTime.now().plusHours(24);
            this.messageCount = 0;
            this.title = "新对话";
        }

        public ChatSession(String sessionId) {
            this(sessionId, "anonymous");
        }

        public void incrementMessageCount() {
            this.messageCount++;
            this.lastAccessed = LocalDateTime.now();
        }

        public void setTitle(String title) {
            if (title != null && !title.trim().isEmpty()) {
                this.title = title.trim();
            }
        }
    }
}