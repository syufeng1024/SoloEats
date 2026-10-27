package com.soloeats.report.service.command;

import java.util.Map;

/**
 * 单条埋点上报。eventType 由 TrackEventType 白名单校验；
 * extra 只允许业务扩展字段，禁止携带手机号、身份证等个人信息。
 */
public record TrackEventCommand(
        String eventType,
        String deviceId,
        String bizType,
        Long bizId,
        String source,
        Map<String, Object> extra
) {
}
