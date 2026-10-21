package com.soloeats.common.auth;

import java.util.Set;

public record UserPrincipal(
        Long userId,
        Long merchantId,
        Long shopId,
        Long riderId,
        Set<String> roles
) {

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
