package org.example.backend.config;

import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import org.example.backend.dto.PageResponse;
import org.example.backend.dto.QuestionDetailDTO;
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
import java.util.List;

@Configuration
public class RedisConfig {

    static {
        // 安全模式配置（防止FastJson反序列化漏洞）
        ParserConfig.getGlobalInstance().setAutoTypeSupport(true);

        // 添加应用包路径
        ParserConfig.getGlobalInstance().addAccept("org.example.backend.");
        ParserConfig.getGlobalInstance().addAccept("java.util.");
        ParserConfig.getGlobalInstance().addAccept("java.time.");
        ParserConfig.getGlobalInstance().addAccept("java.lang.");

        // 添加LangChain4j相关包路径
        ParserConfig.getGlobalInstance().addAccept("dev.langchain4j.");
        ParserConfig.getGlobalInstance().addAccept("dev.langchain4j.data.message.");
        ParserConfig.getGlobalInstance().addAccept("dev.langchain4j.data.");

        // 添加具体的类名
        ParserConfig.getGlobalInstance().addAccept("dev.langchain4j.data.message.UserMessage");
        ParserConfig.getGlobalInstance().addAccept("dev.langchain4j.data.message.AiMessage");
        ParserConfig.getGlobalInstance().addAccept("dev.langchain4j.data.message.SystemMessage");
        ParserConfig.getGlobalInstance().addAccept("dev.langchain4j.data.message.ChatMessage");

        // 添加集合类
        ParserConfig.getGlobalInstance().addAccept("java.util.ArrayList");
        ParserConfig.getGlobalInstance().addAccept("java.util.Collections$");
        ParserConfig.getGlobalInstance().addAccept("java.util.ImmutableCollections$");
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // 使用FastJson序列化器
        FastJsonRedisSerializer<Object> serializer = createFastJsonRedisSerializer();

        // 配置序列化器
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        // 启用事务支持
        template.setEnableTransactionSupport(true);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 创建支持LangChain4j的FastJson序列化器
     */
    private FastJsonRedisSerializer<Object> createFastJsonRedisSerializer() {
        FastJsonRedisSerializer<Object> serializer = new FastJsonRedisSerializer<>(Object.class);

        // 配置FastJson以支持类型信息
        com.alibaba.fastjson.JSON.DEFAULT_GENERATE_FEATURE |=
                SerializerFeature.WriteClassName.getMask();
        com.alibaba.fastjson.JSON.DEFAULT_GENERATE_FEATURE |=
                SerializerFeature.WriteMapNullValue.getMask();
        com.alibaba.fastjson.JSON.DEFAULT_GENERATE_FEATURE |=
                SerializerFeature.WriteDateUseDateFormat.getMask();

        return serializer;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // PageResponse 序列化器
        FastJsonRedisSerializer<PageResponse> pageResponseSerializer =
                new FastJsonRedisSerializer<>(PageResponse.class);

        // QuestionDetailDTO 序列化器
        FastJsonRedisSerializer<QuestionDetailDTO> detailSerializer =
                new FastJsonRedisSerializer<>(QuestionDetailDTO.class);

        // AI会话序列化器
        FastJsonRedisSerializer<SessionManager.ChatSession> sessionSerializer =
                new FastJsonRedisSerializer<>(SessionManager.ChatSession.class);

        // ChatMessage序列化器（用于LangChain4j消息存储）- 使用增强版本
        FastJsonRedisSerializer<Object> chatMessageSerializer = createFastJsonRedisSerializer();

        // 通用配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        createFastJsonRedisSerializer()));

        // AI会话缓存配置（较长的TTL）
        RedisCacheConfiguration sessionConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24)) // 会话保持24小时
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(sessionSerializer));

        // 聊天消息缓存配置
        RedisCacheConfiguration chatMemoryConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(chatMessageSerializer));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("questionList", defaultConfig
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(pageResponseSerializer)))
                .withCacheConfiguration("questionsDetailById", defaultConfig
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(detailSerializer)))
                .withCacheConfiguration("chat:sessions", sessionConfig) // AI会话缓存
                .withCacheConfiguration("chat:memory", chatMemoryConfig) // 聊天内存缓存
                .build();
    }

    /**
     * 配置ChatMessage的FastJson序列化器
     * 用于LangChain4j的消息序列化
     */
    @Bean
    public FastJsonRedisSerializer<Object> chatMessageFastJsonSerializer() {
        return createFastJsonRedisSerializer();
    }
}