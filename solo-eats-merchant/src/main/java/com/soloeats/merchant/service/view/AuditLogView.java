package com.soloeats.merchant.service.view;

import java.time.LocalDateTime;

public record AuditLogView(
        Long id,
        Integer fromStatus,
        Integer toStatus,
        String action,
        Long operatorId,
        String operatorName,
        String remark,
        LocalDateTime createdAt
) {
}
