package org.example.backend.config;

import org.example.backend.model.ChatSessionEntity;
import org.example.backend.service.ai.session.SessionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    // ========== 通用序列化器 Bean ==========
    @Bean
    public FastJson2RedisSerializer<Object> fastJson2RedisSerializer() {
        // 通用Object类型序列化器（支持所有信任包内的类）
        return new FastJson2RedisSerializer<>(Object.class);
    }

    // ========== 专用序列化器 Bean ==========
    @Bean
    public FastJson2RedisSerializer<SessionManager.ChatSession> chatSessionSerializer() {
        // 会话专用序列化器（指定具体类型，避免类型转换问题）
        return new FastJson2RedisSerializer<>(SessionManager.ChatSession.class);
    }

    @Bean
    public FastJson2RedisSerializer<ChatSessionEntity> chatSessionEntitySerializer() {
        // 会话实体专用序列化器
        return new FastJson2RedisSerializer<>(ChatSessionEntity.class);
    }

    // ========== RedisTemplate 配置 ==========
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory,
                                                       FastJson2RedisSerializer<Object> fastJson2RedisSerializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // 键序列化器：String
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 值序列化器：FastJSON2（全局通用）
        template.setValueSerializer(fastJson2RedisSerializer);
        template.setHashValueSerializer(fastJson2RedisSerializer);

        // 启用事务支持
        template.setEnableTransactionSupport(true);
        template.afterPropertiesSet();
        return template;
    }

    // ========== RedisCacheManager 配置 ==========
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory,
                                          FastJson2RedisSerializer<Object> fastJson2RedisSerializer,
                                          FastJson2RedisSerializer<SessionManager.ChatSession> chatSessionSerializer,
                                          FastJson2RedisSerializer<ChatSessionEntity> chatSessionEntitySerializer) {
        // 通用缓存配置（默认10分钟过期）
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(fastJson2RedisSerializer));

        // AI会话缓存配置（24小时过期，使用专用序列化器）
        RedisCacheConfiguration sessionConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(chatSessionSerializer));

        // 会话实体缓存配置（24小时过期，专用序列化器）
        RedisCacheConfiguration sessionEntityConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(chatSessionEntitySerializer));

        // 构建缓存管理器
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("chat:sessions", sessionConfig)          // 会话缓存
                .withCacheConfiguration("chat:sessionEntity", sessionEntityConfig) // 实体缓存
                .withCacheConfiguration("chat:memory", defaultConfig)        // 聊天内存缓存
                .build();
    }
}