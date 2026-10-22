package com.soloeats.common.auth;

import java.util.Set;

/**
 * 登录身份。tenantId/userId 供 T02 的 DataScope 解析「本租户/本人」，
 * merchantId/shopId/riderId 对应「本商家/本门店/骑手本人」。
 */
public record UserPrincipal(
        Long tenantId,
        Long userId,
        String username,
        Long merchantId,
        Long shopId,
        Long riderId,
        Set<String> roles
) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
