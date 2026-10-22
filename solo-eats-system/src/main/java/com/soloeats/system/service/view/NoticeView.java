package com.soloeats.system.service.view;

import java.time.LocalDateTime;

public record NoticeView(
        Long id,
        String title,
        String content,
        Integer noticeType,
        Integer targetType,
        Integer status,
        LocalDateTime publishAt,
        LocalDateTime expireAt,
        Integer readCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
