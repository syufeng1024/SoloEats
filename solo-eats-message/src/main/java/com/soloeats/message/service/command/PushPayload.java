package com.soloeats.message.service.command;

/**
 * 统一下发载荷。messageId 为关联站内信 ID（纯 WS 通知为空），
 * notifyType 对应 t_notify_setting.notify_type。
 */
public record PushPayload(
        Integer notifyType,
        String type,
        Long messageId,
        Long orderId,
        String orderNo,
        Integer status,
        String title,
        String content
) {
}
