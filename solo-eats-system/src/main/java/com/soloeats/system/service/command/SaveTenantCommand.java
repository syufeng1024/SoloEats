package com.soloeats.system.service.command;

import java.time.LocalDateTime;

/**
 * 租户新增/修改入参。修改时 tenantCode 不可变更，传值将被忽略。
 */
public record SaveTenantCommand(
        String tenantCode,
        String tenantName,
        Integer tenantType,
        Long merchantId,
        Long packageId,
        LocalDateTime expireAt,
        String contactName,
        String contactPhone
) {
}
