package com.soloeats.message.service.view;

import com.soloeats.message.domain.entity.MessageTemplate;

import java.time.LocalDateTime;

public record MessageTemplateView(
        Long id,
        String templateCode,
        String templateName,
        Integer templateType,
        String titleTpl,
        String contentTpl,
        String channels,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MessageTemplateView from(MessageTemplate template) {
        return new MessageTemplateView(
                template.getId(),
                template.getTemplateCode(),
                template.getTemplateName(),
                template.getTemplateType(),
                template.getTitleTpl(),
                template.getContentTpl(),
                template.getChannels(),
                template.getStatus(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }
}
