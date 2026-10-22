package com.soloeats.system.service.view;

import java.time.LocalDateTime;

public record SysRoleView(
        Long id,
        Long tenantId,
        String roleCode,
        String roleName,
        Integer roleType,
        Integer dataScope,
        Integer sort,
        Integer status,
        String remark,
        LocalDateTime createdAt
) {
}
