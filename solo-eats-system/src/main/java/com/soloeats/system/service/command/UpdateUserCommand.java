package com.soloeats.system.service.command;

import java.util.List;

/**
 * 修改账号资料（不含 username / password）。roleIds 为 null 表示不动授权，非 null 表示全量替换。
 */
public record UpdateUserCommand(
        String nickname,
        String phone,
        Integer userType,
        Long merchantId,
        Long shopId,
        Long riderId,
        Long deptId,
        Integer dataScope,
        List<Long> roleIds
) {
}
