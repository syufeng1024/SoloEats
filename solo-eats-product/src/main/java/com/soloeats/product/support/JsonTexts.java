package com.soloeats.product.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 商品域 JSON 字段读写。写入统一序列化，读取失败按空集合处理，不把脏数据抛给前端。
 */
public final class JsonTexts {

    private JsonTexts() {
    }

    public static String write(ObjectMapper objectMapper, Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "JSON 序列化失败");
        }
    }

    public static List<String> readStringList(ObjectMapper objectMapper, String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    public static Map<String, String> readStringMap(ObjectMapper objectMapper, String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }
}
