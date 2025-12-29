package org.example.backend.utils;

import com.alibaba.fastjson2.*;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON与Map互转的JPA转换器（适配FastJSON2最新版本）
 */
@Slf4j
@Converter(autoApply = true)
public class JsonConverter implements AttributeConverter<Map<String, Object>, String> {

    // FastJSON2序列化特性（根据源码中的Feature枚举定义）
    private static final JSONWriter.Feature[] SERIALIZE_FEATURES = {
            JSONWriter.Feature.WriteMapNullValue,        // 保留null值 (mask: 16L)
            JSONWriter.Feature.WriteEnumsUsingName,      // 枚举使用名称 (mask: 8192L)
            JSONWriter.Feature.WriteNonStringValueAsString, // 非字符串值作为字符串写入 (mask: 256L)
            JSONWriter.Feature.BrowserCompatible         // 浏览器兼容模式 (mask: 32L)
    };

    // FastJSON2反序列化特性
    private static final JSONReader.Feature[] DESERIALIZE_FEATURES = {
            JSONReader.Feature.SupportAutoType,         // 支持自动类型
            JSONReader.Feature.UseNativeObject,         // 使用原生对象
            JSONReader.Feature.AllowUnQuotedFieldNames, // 允许未引号字段名
            JSONReader.Feature.SupportArrayToBean       // 支持数组转Bean
    };

    // 定义Map类型的TypeReference（用于泛型类型解析）
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE_REFERENCE =
            new TypeReference<LinkedHashMap<String, Object>>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) {
            return null;
        }

        try {
            // FastJSON2最新版：使用toJSONString方法，直接传入特性
            return JSON.toJSONString(attribute, SERIALIZE_FEATURES);
        } catch (Exception e) {
            log.error("FastJSON2序列化失败: {}", e.getMessage(), e);
            return "{}";
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new LinkedHashMap<>(); // 保持插入顺序
        }

        try {
            // 直接使用 TypeReference 的 getType() 方法
            Type mapType = MAP_TYPE_REFERENCE.getType();
            return JSON.parseObject(dbData, mapType, DESERIALIZE_FEATURES);
        } catch (Exception e) {
            log.error("FastJSON2反序列化失败，尝试修复格式...", e);

            // 尝试修复常见的JSON格式问题
            try {
                String fixedJson = fixJsonFormat(dbData);
                Type mapType = MAP_TYPE_REFERENCE.getType();
                return JSON.parseObject(fixedJson, mapType, DESERIALIZE_FEATURES);
            } catch (Exception ex) {
                log.error("FastJSON2反序列化完全失败: {}", ex.getMessage(), ex);
                return convertFallback(dbData);
            }
        }
    }

    /**
     * 备用转换方法：处理特殊情况
     */
    private Map<String, Object> convertFallback(String dbData) {
        try {
            // 尝试直接解析为JSONObject
            JSONObject jsonObject = JSON.parseObject(dbData);
            if (jsonObject != null) {
                Type mapType = MAP_TYPE_REFERENCE.getType();
                return jsonObject.to(mapType, DESERIALIZE_FEATURES);
            }

            // 尝试解析为JSONArray
            JSONArray jsonArray = JSON.parseArray(dbData);
            if (jsonArray != null) {
                Map<String, Object> result = new LinkedHashMap<>();
                Type mapType = MAP_TYPE_REFERENCE.getType();
                result.put("data", jsonArray.to(mapType));
                return result;
            }

            // 尝试解析为基本类型
            Object parsed = JSON.parse(dbData);
            if (parsed != null && !(parsed instanceof JSONObject || parsed instanceof JSONArray)) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("value", parsed);
                return result;
            }
        } catch (Exception e) {
            log.warn("备用转换方法失败: {}", e.getMessage());
        }

        return new LinkedHashMap<>();
    }

    /**
     * 修复常见的JSON格式问题
     */
    private String fixJsonFormat(String json) {
        if (json == null) {
            return "{}";
        }

        String trimmed = json.trim();

        // 空字符串或null
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
            return "{}";
        }

        // 如果已经是有效的JSON对象或数组，直接返回
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return trimmed;
        }

        // 尝试解析为字符串
        try {
            // 如果是数字、布尔值等基本类型
            if (trimmed.matches("-?\\d+(\\.\\d+)?") ||
                    "true".equalsIgnoreCase(trimmed) ||
                    "false".equalsIgnoreCase(trimmed)) {
                return "{\"value\": " + trimmed + "}";
            }

            // 如果是带引号的字符串
            if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                    (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
                return "{\"value\": " + trimmed + "}";
            }

            // 其他情况视为字符串
            return "{\"value\": \"" + trimmed.replace("\"", "\\\"") + "\"}";
        } catch (Exception e) {
            log.warn("JSON格式修复失败，返回空对象: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 备用方法：使用JSONObject直接处理
     */
    private Map<String, Object> convertUsingJSONObject(String dbData) {
        try {
            JSONObject jsonObject = JSON.parseObject(dbData);
            if (jsonObject == null) {
                return new LinkedHashMap<>();
            }

            // 将JSONObject转换为Map
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : jsonObject.keySet()) {
                Object value = jsonObject.get(key);
                result.put(key, value);
            }
            return result;
        } catch (Exception e) {
            log.error("使用JSONObject转换失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }
}