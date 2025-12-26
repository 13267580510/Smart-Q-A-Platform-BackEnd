package org.example.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
@Configuration
@EnableScheduling
public class SessionCleanupConfig {

    private final RedisTemplate<String, Object> redisTemplate;

    public SessionCleanupConfig(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Redis有自动过期机制，定时任务主要处理逻辑清理
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void cleanupExpiredSessions() {
        // Redis自动处理key过期，这里可以添加额外的清理逻辑
        System.out.println("检查Redis会话状态...");
    }
}