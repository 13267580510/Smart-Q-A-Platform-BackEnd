package org.example.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.backend.dto.PageResponse;
import org.example.backend.dto.QuestionResponseDTO;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

public class CustomRedisCacheConfiguration {

    public static RedisSerializationContext.SerializationPair<Object> getSerializationPair() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册 JavaTimeModule 以支持 Java 8 日期时间类型
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);
        return RedisSerializationContext.SerializationPair.fromSerializer(serializer);
    }

    public static RedisSerializationContext<String, Object> getRedisSerializationContext() {
        return RedisSerializationContext.<String, Object>newSerializationContext()
                .key( new StringRedisSerializer())
                .value(getSerializationPair())
                .hashKey(new StringRedisSerializer())
                .hashValue(getSerializationPair())
                .build();
    }
}