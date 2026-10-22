package com.soloeats.system.service.view;

import java.time.LocalDateTime;

/**
 * 租户视图。status=2 表示按 expire_at 派生的过期态，库内仍为 0。
 */
public record TenantView(
        Long id,
        String tenantCode,
        String tenantName,
        Integer tenantType,
        Long merchantId,
        Long packageId,
        Integer status,
        LocalDateTime expireAt,
        String contactName,
        String contactPhone,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
