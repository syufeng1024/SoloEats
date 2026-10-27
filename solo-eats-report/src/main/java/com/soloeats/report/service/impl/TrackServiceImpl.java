package com.soloeats.report.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.report.domain.entity.TrackEvent;
import com.soloeats.report.domain.enums.TrackEventType;
import com.soloeats.report.mapper.TrackEventMapper;
import com.soloeats.report.service.TrackService;
import com.soloeats.report.service.command.TrackEventCommand;
import com.soloeats.system.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 埋点同步落库。
 *
 * <p>单次上限 20 条；eventType 走白名单；字符串字段去换行并按列宽截断；
 * extra 中的手机号/身份证字段直接丢弃，避免个人信息进入 t_track_event。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackServiceImpl implements TrackService {

    private static final int MAX_EVENTS = 20;
    private static final int DEVICE_ID_MAX = 64;
    private static final int BIZ_TYPE_MAX = 32;
    private static final int SOURCE_MAX = 32;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final TrackEventMapper trackEventMapper;
    private final ObjectMapper objectMapper;

    @Override
    public int track(Long userId, List<TrackEventCommand> events) {
        if (events == null || events.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "events 不能为空");
        }
        if (events.size() > MAX_EVENTS) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单次最多上报 " + MAX_EVENTS + " 条事件");
        }

        LocalDateTime eventAt = LocalDateTime.now(ZONE);
        int written = 0;
        for (TrackEventCommand event : events) {
            TrackEventType type = TrackEventType.parse(event.eventType());
            TrackEvent entity = new TrackEvent();
            entity.setTenantId(TenantContext.get());
            entity.setEventType(type.name());
            entity.setUserId(userId);
            entity.setDeviceId(clean(event.deviceId(), DEVICE_ID_MAX));
            entity.setBizType(clean(event.bizType(), BIZ_TYPE_MAX));
            entity.setBizId(event.bizId());
            entity.setSource(clean(event.source(), SOURCE_MAX));
            entity.setExtraJson(toSafeJson(event.extra()));
            entity.setEventAt(eventAt);
            written += trackEventMapper.insert(entity);
        }
        log.debug("track events written userId={} count={}", userId, written);
        return written;
    }

    /** 去掉换行/制表符并按列宽截断；空白视为 null。 */
    private static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    /**
     * extra 只保留业务扩展字段。键名包含 phone/mobile/idcard 的一律丢弃；
     * 无可保留字段时返回 null，避免写入空 JSON 对象。
     */
    private String toSafeJson(Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : extra.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.contains("phone") || lower.contains("mobile")
                    || lower.contains("idcard") || lower.contains("id_card")) {
                continue;
            }
            safe.put(key, entry.getValue());
        }
        if (safe.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "extra 不是合法 JSON");
        }
    }
}
