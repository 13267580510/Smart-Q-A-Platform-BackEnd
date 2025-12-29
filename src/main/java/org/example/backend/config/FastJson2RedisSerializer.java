package org.example.backend.config;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.filter.Filter;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.util.Arrays;

/**
 * 通用的FastJSON2 Redis序列化器（兼容版本）
 */
public class FastJson2RedisSerializer<T> implements RedisSerializer<T> {
    // 安全的白名单过滤器
    public static final Filter AUTO_TYPE_FILTER = JSONReader.autoTypeFilter(
            "org.example.backend.",
            "java.util.",
            "java.time.",
            "dev.langchain4j.",
            "org.example.backend.model.",
            "org.example.backend.service.ai.session."
    );

    private final Class<T> clazz;

    // 动态确定可用的特性
    private final JSONWriter.Feature[] writeFeatures;
    private final JSONReader.Feature[] readFeatures;

    public FastJson2RedisSerializer(Class<T> clazz) {
        this.clazz = clazz;
        this.writeFeatures = determineWriteFeatures();
        this.readFeatures = determineReadFeatures();
    }

    @Override
    public byte[] serialize(T t) throws SerializationException {
        if (t == null) {
            return new byte[0];
        }
        try {
            return JSON.toJSONBytes(t, writeFeatures);
        } catch (Exception e) {
            throw new SerializationException("序列化失败", e);
        }
    }

    @Override
    public T deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            return JSON.parseObject(bytes, clazz, AUTO_TYPE_FILTER, readFeatures);
        } catch (Exception e) {
            throw new SerializationException("反序列化失败", e);
        }
    }

    /**
     * 确定可用的序列化特性
     */
    private JSONWriter.Feature[] determineWriteFeatures() {
        // 基础特性
        JSONWriter.Feature[] baseFeatures = {
                JSONWriter.Feature.WriteClassName,
                JSONWriter.Feature.WriteMapNullValue
        };

        // 尝试添加版本特定的特性
        for (JSONWriter.Feature feature : JSONWriter.Feature.values()) {
            String name = feature.name();
            if ("WriteDateUseISO8601".equals(name) ||
                    "WriteDateFormat".equals(name) ||
                    "ISO8601".equals(name)) {
                return addFeature(baseFeatures, feature);
            }
        }
        return baseFeatures;
    }

    /**
     * 确定可用的反序列化特性
     */
    private JSONReader.Feature[] determineReadFeatures() {
        // 基础特性
        JSONReader.Feature[] baseFeatures = {
                JSONReader.Feature.SupportAutoType
        };

        // 尝试添加版本特定的特性
        for (JSONReader.Feature feature : JSONReader.Feature.values()) {
            String name = feature.name();
            if ("IgnoreNulls".equals(name) ||
                    "SupportAutoType".equals(name)) {
                return addFeature(baseFeatures, feature);
            }
        }
        return baseFeatures;
    }

    /**
     * 添加特性到数组
     */
    private JSONWriter.Feature[] addFeature(JSONWriter.Feature[] array, JSONWriter.Feature feature) {
        JSONWriter.Feature[] newArray = Arrays.copyOf(array, array.length + 1);
        newArray[array.length] = feature;
        return newArray;
    }

    private JSONReader.Feature[] addFeature(JSONReader.Feature[] array, JSONReader.Feature feature) {
        JSONReader.Feature[] newArray = Arrays.copyOf(array, array.length + 1);
        newArray[array.length] = feature;
        return newArray;
    }
}