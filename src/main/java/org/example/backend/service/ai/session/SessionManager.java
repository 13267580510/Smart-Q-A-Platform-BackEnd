package org.example.backend.service.ai.session;

import lombok.Data;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionManager {

    @Getter
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 内存中的会话缓存（热数据缓存）
    @Getter
    private final Map<String, ChatSession> sessionCache = new ConcurrentHashMap<>();

    // 用户会话映射：userId -> sessionIds
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    // Redis键前缀
    private static final String SESSION_KEY_PREFIX = "chat:session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "chat:user:sessions:";
    private static final Duration DEFAULT_EXPIRY = Duration.ofHours(24);

    /**
     * 获取或创建会话（关联用户）
     */
    public synchronized String getOrCreateSession(String sessionId, String userId) {
        if (sessionId == null || sessionId.equals("default")) {
            sessionId = generateSessionId();
        }

        // 先检查缓存
        ChatSession cachedSession = sessionCache.get(sessionId);
        if (cachedSession != null) {
            // 更新最后访问时间
            cachedSession.setLastAccessed(LocalDateTime.now());
            cachedSession.setExpiryTime(LocalDateTime.now().plus(DEFAULT_EXPIRY));
            cachedSession.setUserId(userId);

            // 同步到Redis
            saveToRedis(sessionId, cachedSession);

            // 添加到用户会话列表
            addSessionToUser(userId, sessionId);

            return sessionId;
        }

        // 从Redis加载
        String redisKey = SESSION_KEY_PREFIX + sessionId;
        ChatSession session = (ChatSession) redisTemplate.opsForValue().get(redisKey);

        if (session == null) {
            session = new ChatSession(sessionId, userId);
        } else {
            session.setUserId(userId); // 确保会话有userId
        }

        session.setLastAccessed(LocalDateTime.now());
        session.setExpiryTime(LocalDateTime.now().plus(DEFAULT_EXPIRY));

        // 保存到Redis
        saveToRedis(sessionId, session);

        // 更新缓存
        sessionCache.put(sessionId, session);

        // 添加到用户会话列表
        addSessionToUser(userId, sessionId);

        return sessionId;
    }

    /**
     * 获取或创建会话（无用户ID版本，向后兼容）
     */
    public synchronized String getOrCreateSession(String sessionId) {
        return getOrCreateSession(sessionId, "anonymous");
    }

    /**
     * 添加会话到用户列表
     */
    private void addSessionToUser(String userId, String sessionId) {
        if (userId == null || "anonymous".equals(userId)) {
            return;
        }

        String userKey = USER_SESSIONS_KEY_PREFIX + userId;

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

        // 限制每个用户的会话数量（例如最多100个）
        if (newSessionIds.size() > 100) {
            // 移除最旧的会话
            Iterator<String> iterator = newSessionIds.iterator();
            while (newSessionIds.size() > 100) {
                String oldestSessionId = null;
                while (iterator.hasNext()) {
                    oldestSessionId = iterator.next();
                }
                if (oldestSessionId != null) {
                    newSessionIds.remove(oldestSessionId);
                }
            }
        }

        // 保存回Redis
        redisTemplate.opsForValue().set(userKey, newSessionIds, DEFAULT_EXPIRY);

        // 更新内存缓存
        userSessions.put(userId, newSessionIds);
    }

    /**
     * 获取用户的所有会话
     */
    public List<ChatSession> getUserSessions(String userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        // 从内存缓存获取
        Set<String> sessionIds = userSessions.get(userId);
        if (sessionIds == null) {
            // 从Redis获取
            String userKey = USER_SESSIONS_KEY_PREFIX + userId;
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
            ChatSession session = getSessionInfo(sessionId);
            if (session != null && session.getUserId() != null && session.getUserId().equals(userId)) {
                sessions.add(session);
            }
        }

        // 按最后访问时间排序（最新的在前）
        sessions.sort((s1, s2) -> s2.getLastAccessed().compareTo(s1.getLastAccessed()));

        return sessions;
    }

    /**
     * 从用户会话列表中移除会话
     */
    public void removeSessionFromUser(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            return;
        }

        // 从Redis获取用户会话列表
        String userKey = USER_SESSIONS_KEY_PREFIX + userId;
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
                redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
                return false;
            }
            // 更新最后访问时间
            cachedSession.setLastAccessed(LocalDateTime.now());
            return true;
        }

        // 从Redis检查
        String key = SESSION_KEY_PREFIX + sessionId;
        ChatSession session = (ChatSession) redisTemplate.opsForValue().get(key);

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

        // 获取会话信息以获取userId
        ChatSession session = getSessionInfo(sessionId);

        // 从内存缓存删除
        sessionCache.remove(sessionId);

        // 从Redis删除
        String key = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.delete(key);

        // 从用户会话列表中移除
        if (session != null && session.getUserId() != null) {
            removeSessionFromUser(session.getUserId(), sessionId);
        }
    }

    /**
     * 获取会话信息
     */
    public ChatSession getSessionInfo(String sessionId) {
        if (sessionId == null) {
            return null;
        }

        // 先检查内存缓存
        ChatSession cachedSession = sessionCache.get(sessionId);
        if (cachedSession != null) {
            return cachedSession;
        }

        // 从Redis获取
        String key = SESSION_KEY_PREFIX + sessionId;
        ChatSession session = (ChatSession) redisTemplate.opsForValue().get(key);

        if (session != null) {
            // 更新内存缓存
            sessionCache.put(sessionId, session);
        }

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
        System.out.println("SessionManager.getUserSessions被调用，userId: " + userId);
        for (ChatSession session : sessions) {
            if (!isValidSession(session.getSessionId())) {
                removeSession(session.getSessionId());
            }
        }
    }

    /**
     * 其他原有方法保持不变，只需添加userId参数
     */
    public void incrementMessageCount(String sessionId) {
        ChatSession session = getSessionInfo(sessionId);
        if (session != null) {
            session.incrementMessageCount();
            session.setLastAccessed(LocalDateTime.now());
            session.setExpiryTime(LocalDateTime.now().plus(DEFAULT_EXPIRY));

            saveToRedis(sessionId, session);
            sessionCache.put(sessionId, session);
        }
    }

    public long getActiveSessionCount() {
        return sessionCache.size();
    }


    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        sessionCache.entrySet().removeIf(entry -> {
            if (now.isAfter(entry.getValue().getExpiryTime())) {
                String sessionId = entry.getKey();
                ChatSession session = entry.getValue();

                // 从Redis删除
                redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);

                // 从用户会话列表移除
                if (session.getUserId() != null) {
                    removeSessionFromUser(session.getUserId(), sessionId);
                }

                return true;
            }
            return false;
        });
    }

    private void saveToRedis(String sessionId, ChatSession session) {
        String key = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, session, DEFAULT_EXPIRY);
    }

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
        private String title; // 新增：会话标题

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