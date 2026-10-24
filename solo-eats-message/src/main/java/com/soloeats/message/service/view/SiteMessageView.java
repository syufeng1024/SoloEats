package com.soloeats.message.service.view;

import com.soloeats.message.domain.entity.SiteMessage;

import java.time.LocalDateTime;

public record SiteMessageView(
        Long id,
        Integer receiverType,
        Long receiverId,
        String templateCode,
        Integer msgType,
        String title,
        String content,
        String bizType,
        Long bizId,
        Integer readFlag,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {

    public static SiteMessageView from(SiteMessage message) {
        return new SiteMessageView(
                message.getId(),
                message.getReceiverType(),
                message.getReceiverId(),
                message.getTemplateCode(),
                message.getMsgType(),
                message.getTitle(),
                message.getContent(),
                message.getBizType(),
                message.getBizId(),
                message.getReadFlag(),
                message.getReadAt(),
                message.getCreatedAt());
    }
}
