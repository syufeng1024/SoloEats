package com.soloeats.app.dto;

import jakarta.validation.constraints.AssertTrue;

/**
 * 双模式登录请求：
 * 1. 账号密码：{username, password}；
 * 2. 旧契约模拟登录：{role, userId, merchantId, shopId, riderId}。
 * 两组字段必须且只能提供一组。
 */
public record LoginRequest(
        String role,

        Long userId,

        Long merchantId,

        Long shopId,

        Long riderId,

        String username,

        String password
) {

    @AssertTrue(message = "username/password 与 role 必须且只能提供一组")
    public boolean isCredentialModeValid() {
        boolean passwordMode = hasText(username) && hasText(password);
        boolean mockMode = hasText(role);
        return passwordMode ^ mockMode;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
