package com.soloeats.system.service.command;

import java.util.List;

/**
 * 创建后台/商家/骑手账号。password 为明文，落库前由 PasswordHasher 转 bcrypt。
 */
public record CreateUserCommand(
        String username,
        String password,
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
