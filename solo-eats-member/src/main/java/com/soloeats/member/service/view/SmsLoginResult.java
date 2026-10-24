package com.soloeats.member.service.view;

import java.util.Set;

/**
 * C 端短信登录结果。userId 即 t_member_user.id；roles 固定含 C_USER。
 */
public record SmsLoginResult(
        String token,
        String tokenType,
        long expiresIn,
        Long userId,
        String nickname,
        boolean isNewUser,
        Set<String> roles
) {
}
