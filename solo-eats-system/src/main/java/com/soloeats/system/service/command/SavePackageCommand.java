package com.soloeats.system.service.command;

import java.util.List;

/**
 * 套餐新增/修改入参。price 单位为分。
 */
public record SavePackageCommand(
        String packageCode,
        String packageName,
        List<Long> menuIds,
        Integer maxShop,
        Integer maxUser,
        Long price,
        Integer validDays,
        Integer status,
        String remark
) {
}
