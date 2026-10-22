package com.soloeats.system.service.view;

import java.time.LocalDateTime;

public record MailLogView(
        Long id,
        String templateCode,
        String toAddr,
        String subject,
        Integer status,
        String errorMsg,
        LocalDateTime sentAt,
        LocalDateTime createdAt
) {
}
