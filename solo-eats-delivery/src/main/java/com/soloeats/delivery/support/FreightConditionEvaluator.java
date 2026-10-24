package com.soloeats.delivery.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.time.LocalTime;

/**
 * t_freight_rule.condition_json 解析。试算与骑手补贴共用同一套窗口/开关语义。
 * ObjectMapper 只做只读 readTree，静态复用线程安全。
 */
public final class FreightConditionEvaluator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FreightConditionEvaluator() {
    }

    /** 空条件返回 null；非法 JSON 也返回 null，由调用方决定跳过该规则。 */
    public static JsonNode parse(String conditionJson) {
        if (!StringUtils.hasText(conditionJson)) {
            return null;
        }
        try {
            return MAPPER.readTree(conditionJson);
        } catch (Exception ex) {
            return null;
        }
    }

    /** 支持 {"windows":[...]} 与单窗口 {"start":"11:00","end":"13:00"}；左闭右开。 */
    public static boolean inWindows(JsonNode condition, LocalTime time) {
        if (condition == null) {
            return false;
        }
        JsonNode windows = condition.path("windows");
        if (windows.isArray()) {
            for (JsonNode window : windows) {
                if (inWindow(window, time)) {
                    return true;
                }
            }
            return false;
        }
        return inWindow(condition, time);
    }

    public static boolean weatherEnabled(JsonNode condition) {
        return condition != null && condition.path("enabled").asBoolean(false);
    }

    private static boolean inWindow(JsonNode window, LocalTime time) {
        LocalTime start = parseTime(window.path("start").asText(null));
        LocalTime end = parseTime(window.path("end").asText(null));
        if (start == null || end == null || start.equals(end)) {
            return false;
        }
        // 跨零点窗口（22:00-06:00）拆成两段
        if (start.isAfter(end)) {
            return !time.isBefore(start) || time.isBefore(end);
        }
        return !time.isBefore(start) && time.isBefore(end);
    }

    private static LocalTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
