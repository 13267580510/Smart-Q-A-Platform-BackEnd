package org.example.backend.service.ai.session;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.Filter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.config.CacheKeyConfig;
import org.example.backend.model.ChatSessionEntity;
import org.example.backend.repository.ChatSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class ChatSessionService {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    @Autowired
    private final ObjectMapper objectMapper;

    // 缓存配置
    private static final long CACHE_EXPIRE_HOURS = 6; // 缓存6小时
    private static final long CACHE_TTL_HOURS = 24;

    public ChatSessionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 创建或更新会话
     */
    public ChatSessionEntity saveOrUpdateSession(String sessionId, Long userId, String title) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId不能为空");
        }

        try {
            // 查找现有会话
            Optional<ChatSessionEntity> existingSession = chatSessionRepository.findById(sessionId);
            ChatSessionEntity session;

            if (existingSession.isPresent()) {

                // 更新现有会话
                session = existingSession.get();
                session.setLastAccessed(LocalDateTime.now());
                session.setExpiryTime(LocalDateTime.now().plusHours(24));
                if (title != null && !title.trim().isEmpty()) {
                    session.setTitle(title);
                }
                if (userId != null) {
                    session.setUserId(userId);
                }
                log.debug("更新会话: sessionId={}, userId={}", sessionId, userId);
            } else {
                // 创建新会话
                session = ChatSessionEntity.builder()
                        .sessionId(sessionId)
                        .userId(userId != null ? userId : 0L)
                        .title(title != null ? title : "新对话")
                        .createdAt(LocalDateTime.now())
                        .lastAccessed(LocalDateTime.now())
                        .expiryTime(LocalDateTime.now().plusHours(24))
                        .messageCount(0)
                        .isActive(true)
                        .metadata(new HashMap<>())
                        .build();
                log.info("创建新会话: sessionId={}, userId={}, title={}", sessionId, userId, title);
            }

            // 保存到数据库
            ChatSessionEntity savedSession = chatSessionRepository.save(session);
            System.out.println("准备将会话缓存redis");
            // 异步更新Redis缓存
            CompletableFuture.runAsync(() -> {
                try {
                    System.out.println("开始缓存会话到redis");
                    cacheSession(userId,session);
                    log.debug("缓存会话信息: sessionId={}", sessionId);
                } catch (Exception e) {
                    log.error("缓存会话失败: sessionId={}", sessionId, e);
                }
            });

            return savedSession;

        } catch (Exception e) {
            log.error("保存会话失败: sessionId={}, userId={}", sessionId, userId, e);
            throw new RuntimeException("保存会话失败", e);
        }
    }

    /**
     * 获取会话信息
     */
    public ChatSessionEntity getSession(Long userId,String sessionId) {
        if (sessionId == null) {
            return null;
        }

        try {
            // 1. 尝试从Redis缓存获取
            ChatSessionEntity cachedSession = getCachedSession(sessionId);
            if (cachedSession != null) {
                log.debug("从缓存获取会话: sessionId={}", sessionId);
                return cachedSession;
            }

            // 2. 从数据库获取
            Optional<ChatSessionEntity> sessionOpt = chatSessionRepository.findById(sessionId);
            if (sessionOpt.isPresent()) {
                ChatSessionEntity session = sessionOpt.get();

                // 3. 异步缓存到Redis
                CompletableFuture.runAsync(() -> {
                    try {
                        cacheSession(userId,session);
                    } catch (Exception e) {
                        log.error("缓存会话失败: sessionId={}", sessionId, e);
                    }
                });

                return session;
            }

            log.debug("会话不存在: sessionId={}", sessionId);
            return null;

        } catch (Exception e) {
            log.error("获取会话失败: sessionId={}", sessionId, e);
            return null;
        }
    }

    /**
     * 获取用户的所有会话（简单字符串缓存方案）
     */
    public List<ChatSessionEntity> getUserSessions(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        try {
            // 1. 尝试从Redis缓存获取
            String cacheKey = CacheKeyConfig.buildUserSessionsKey(String.valueOf(userId));
            System.out.println("cacheKey:" + cacheKey);
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);
            System.out.println("cachedJson:"+cachedJson);
            if (StringUtils.isNotBlank(cachedJson)) {
                try {
                    // 解析JSON数组 - 使用兼容的方式
                    List<ChatSessionEntity> cachedSessions = JSON.parseArray(
                            cachedJson,
                            ChatSessionEntity.class
                    );

                    if (!cachedSessions.isEmpty()) {
                        log.debug("从缓存获取用户会话列表: userId={}, count={}", userId, cachedSessions.size());
                        return cachedSessions;
                    }
                } catch (Exception e) {
                    log.warn("缓存数据解析失败，从数据库重新加载", e);
                    redisTemplate.delete(cacheKey); // 清除无效缓存
                }
            }

            // 2. 从数据库获取
            List<ChatSessionEntity> sessions = chatSessionRepository
                    .findByUserIdOrderByLastAccessedDesc(userId);

            // 过滤活跃会话
            List<ChatSessionEntity> activeSessions = sessions.stream()
                    .filter(session -> Boolean.TRUE.equals(session.getIsActive()))
                    .filter(session -> session.getExpiryTime() == null ||
                            LocalDateTime.now().isBefore(session.getExpiryTime()))
                    .collect(Collectors.toList());

            // 3. 缓存到Redis（使用兼容的FastJSON2序列化）
            if (!activeSessions.isEmpty()) {
                // 简单JSON序列化，不使用复杂的特性
                String jsonString = JSON.toJSONString(activeSessions);
                redisTemplate.opsForValue().set(
                        cacheKey,
                        jsonString,
                        CACHE_EXPIRE_HOURS,
                        TimeUnit.HOURS
                );
                log.debug("用户会话列表已缓存: userId={}, count={}", userId, activeSessions.size());
            }

            return activeSessions;

        } catch (Exception e) {
            log.error("获取用户会话失败: userId={}", userId, e);
            return Collections.emptyList();
        }
    }
    /**
     * 更新会话标题
     */
    public ChatSessionEntity updateSessionTitle(String sessionId, String title, Long userId) {
        if (sessionId == null || title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("参数不能为空");
        }

        try {
            // 获取会话
            Optional<ChatSessionEntity> sessionOpt = chatSessionRepository.findById(sessionId);
            if (!sessionOpt.isPresent()) {
                throw new RuntimeException("会话不存在: " + sessionId);
            }

            ChatSessionEntity session = sessionOpt.get();

            // 权限验证（如果提供了userId）
            if (userId != null && !userId.equals(session.getUserId())) {
                throw new SecurityException("无权修改此会话");
            }

            // 更新标题
            session.setTitle(title.trim());
            session.setLastAccessed(LocalDateTime.now());

            // 保存到数据库
            ChatSessionEntity updatedSession = chatSessionRepository.save(session);

            // 异步更新缓存
            CompletableFuture.runAsync(() -> {
                try {
                    cacheSession(userId,updatedSession);
                    log.debug("更新会话标题缓存: sessionId={}, title={}", sessionId, title);
                } catch (Exception e) {
                    log.error("更新会话缓存失败: sessionId={}", sessionId, e);
                }
            });

            log.info("更新会话标题: sessionId={}, title={}, userId={}", sessionId, title, userId);
            return updatedSession;

        } catch (SecurityException e) {
            throw e; // 重新抛出权限异常
        } catch (Exception e) {
            log.error("更新会话标题失败: sessionId={}, title={}", sessionId, title, e);
            throw new RuntimeException("更新会话标题失败", e);
        }
    }

    /**
     * 删除会话
     */
    public void deleteSession(String sessionId, Long userId) {
        if (sessionId == null) {
            return;
        }

        try {
            // 获取会话信息用于权限验证
            Optional<ChatSessionEntity> sessionOpt = chatSessionRepository.findById(sessionId);
            if (!sessionOpt.isPresent()) {
                log.warn("会话不存在，无需删除: sessionId={}", sessionId);
                return;
            }

            ChatSessionEntity session = sessionOpt.get();

            // 权限验证（如果提供了userId）
            if (userId != null && !userId.equals(session.getUserId())) {
                throw new SecurityException("无权删除此会话");
            }

            // 标记为不活跃而不是物理删除
            session.setIsActive(false);
            session.setLastAccessed(LocalDateTime.now());
            chatSessionRepository.save(session);

            // 清理缓存
            clearSessionCache(sessionId);
            if (session.getUserId() != null) {
                clearUserSessionsCache(session.getUserId());
            }

            log.info("删除会话: sessionId={}, userId={}", sessionId, userId);

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除会话失败: sessionId={}", sessionId, e);
            throw new RuntimeException("删除会话失败", e);
        }
    }

    /**
     * 增加消息计数
     */
    public void incrementMessageCount(Long userId,String sessionId) {
        if (sessionId == null) {
            return;
        }

        try {
            // 从数据库获取
            Optional<ChatSessionEntity> sessionOpt = chatSessionRepository.findById(sessionId);
            if (sessionOpt.isPresent()) {
                ChatSessionEntity session = sessionOpt.get();
                session.setMessageCount(session.getMessageCount() + 1);
                session.setLastAccessed(LocalDateTime.now());

                chatSessionRepository.save(session);

                // 异步更新缓存
                CompletableFuture.runAsync(() -> {
                    try {
                        cacheSession(userId,session);
                    } catch (Exception e) {
                        log.error("更新消息计数缓存失败: sessionId={}", sessionId, e);
                    }
                });

                log.debug("增加消息计数: sessionId={}, count={}", sessionId, session.getMessageCount());
            }
        } catch (Exception e) {
            log.error("增加消息计数失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 清理过期会话
     */
    public int cleanupExpiredSessions() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<ChatSessionEntity> expiredSessions = chatSessionRepository
                    .findByExpiryTimeBefore(now);

            if (expiredSessions.isEmpty()) {
                return 0;
            }

            // 标记为不活跃
            expiredSessions.forEach(session -> {
                session.setIsActive(false);
                session.setLastAccessed(now);
            });

            chatSessionRepository.saveAll(expiredSessions);

            // 清理缓存
            expiredSessions.forEach(session -> {
                clearSessionCache(session.getSessionId());
                if (session.getUserId() != null) {
                    clearUserSessionsCache(session.getUserId());
                }
            });

            log.info("清理过期会话完成: count={}", expiredSessions.size());
            return expiredSessions.size();

        } catch (Exception e) {
            log.error("清理过期会话失败", e);
            return 0;
        }
    }

    // ==================== 缓存相关方法 ====================

    /**
     * 缓存会话信息
     */
    private void cacheSession(Long userId,ChatSessionEntity session) {
        try {
            System.out.println("开始执行cacheSession");
            String key = CacheKeyConfig.buildUserSessionsKey(String.valueOf(userId));
            System.out.println("执行cacheSession.key"+key);
            System.out.println("执行cacheSession.session"+ session);


            redisTemplate.opsForValue().set(
                    key,
                    String.valueOf(session),
                    CACHE_TTL_HOURS,
                    TimeUnit.HOURS
            );
            System.out.println("完成执行cacheSession");
        } catch (Exception e) {
            log.warn("缓存会话失败: sessionId={}", session.getSessionId(), e);
        }
    }

    /**
     * 从缓存获取会话
     */
    private ChatSessionEntity getCachedSession(String sessionId) {
        try {
            String key = CacheKeyConfig.buildSessionKey(sessionId);
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof ChatSessionEntity) {
                return (ChatSessionEntity) cached;
            }
        } catch (Exception e) {
            log.warn("从缓存获取会话失败: sessionId={}", sessionId, e);
        }
        return null;
    }


    /**
     * 清除用户的会话缓存
     */
    public void clearUserSessionsCache(Long userId) {
        if (userId == null) return;

        try {
            String cacheKey = CacheKeyConfig.buildUserSessionsKey(String.valueOf(userId));
            redisTemplate.delete(cacheKey);
            log.debug("用户会话缓存已清除: userId={}", userId);
        } catch (Exception e) {
            log.error("清除用户会话缓存失败: userId={}", userId, e);
        }
    }


    /**
     * 清理会话缓存
     */
    private void clearSessionCache(String sessionId) {
        try {
            String key = CacheKeyConfig.buildSessionKey(sessionId);
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("清理会话缓存失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 诊断缓存问题
     */
    public void diagnoseCacheIssue(Long userId) {
        String cacheKey = CacheKeyConfig.buildUserSessionsKey(String.valueOf(userId));

        try {
            Object cachedData = redisTemplate.opsForValue().get(cacheKey);

            if (cachedData == null) {
                log.info("诊断: 缓存不存在 - key={}", cacheKey);
                return;
            }

            log.info("诊断结果:");
            log.info("  Key: {}", cacheKey);
            log.info("  数据类型: {}", cachedData.getClass().getName());
            log.info("  数据长度: {}", cachedData.toString().length());
            log.info("  数据预览: {}",
                    cachedData.toString().substring(0, Math.min(200, cachedData.toString().length())));

            // 如果是字符串，尝试解析
            if (cachedData instanceof String) {
                try {
                    Object parsed = objectMapper.readValue((String) cachedData, Object.class);
                    log.info("  JSON解析成功，类型: {}", parsed.getClass().getName());
                } catch (Exception e) {
                    log.info("  JSON解析失败: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("诊断缓存失败: userId={}", userId, e);
        }
    }

}