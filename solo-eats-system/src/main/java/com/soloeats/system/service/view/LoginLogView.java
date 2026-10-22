package com.soloeats.system.service.view;

import java.time.LocalDateTime;

public record LoginLogView(
        Long id,
        Long userId,
        String username,
        Integer loginType,
        Integer success,
        String failReason,
        String ip,
        String ua,
        LocalDateTime createdAt
) {
}
