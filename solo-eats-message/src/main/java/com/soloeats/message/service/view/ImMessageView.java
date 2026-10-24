package com.soloeats.message.service.view;

import com.soloeats.message.domain.entity.ImMessage;

import java.time.LocalDateTime;

/**
 * 单条 IM 消息。content 是纯文本，前端按 textContent 渲染；
 * extra 是已经解析好的结构化对象（图片 fileId / 订单卡片 / 坐标），不下发 HTML。
 */
public record ImMessageView(
        Long id,
        Long sessionId,
        Integer senderType,
        Long senderId,
        Integer msgType,
        String content,
        Object extra,
        Integer readFlag,
        LocalDateTime createdAt
) {

    public static ImMessageView from(ImMessage message, Object extra) {
        return new ImMessageView(
                message.getId(),
                message.getSessionId(),
                message.getSenderType(),
                message.getSenderId(),
                message.getMsgType(),
                message.getContent(),
                extra,
                message.getReadFlag(),
                message.getCreatedAt());
    }
}
