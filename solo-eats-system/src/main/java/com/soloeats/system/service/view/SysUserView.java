package com.soloeats.system.service.view;

import java.time.LocalDateTime;

/**
 * 后台账号视图。phone 已脱敏，password_hash 绝不进入视图。
 */
public record SysUserView(
        Long id,
        Long tenantId,
        String username,
        String nickname,
        String phone,
        Integer userType,
        Long merchantId,
        Long shopId,
        Long riderId,
        Long deptId,
        Integer dataScope,
        Integer status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
