package com.soloeats.system.service.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 套餐视图。menuIds 为 null 表示不限制菜单范围。
 */
public record PackageView(
        Long id,
        String packageCode,
        String packageName,
        List<Long> menuIds,
        Integer maxShop,
        Integer maxUser,
        Long price,
        Integer validDays,
        Integer status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
