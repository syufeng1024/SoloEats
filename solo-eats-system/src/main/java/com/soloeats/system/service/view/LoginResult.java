package com.soloeats.system.service.view;

import java.util.Set;

/**
 * 登录结果。permissions 只返回给前端做按钮显隐，不写入 JWT。
 */
public record LoginResult(
        String token,
        String tokenType,
        long expiresIn,
        Set<String> roles,
        Set<String> permissions
) {
}
