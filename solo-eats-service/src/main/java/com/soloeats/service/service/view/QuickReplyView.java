package com.soloeats.service.service.view;

import com.soloeats.service.domain.entity.QuickReply;

import java.time.LocalDateTime;

/**
 * 快捷话术视图。tenantId 与 deleted 属存储细节，不对外返回（与 FaqView 口径一致）。
 */
public record QuickReplyView(
        Long id,
        String category,
        String content,
        Integer sort,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static QuickReplyView from(QuickReply reply) {
        return new QuickReplyView(
                reply.getId(),
                reply.getCategory(),
                reply.getContent(),
                reply.getSort(),
                reply.getStatus(),
                reply.getCreatedAt(),
                reply.getUpdatedAt()
        );
    }
}
