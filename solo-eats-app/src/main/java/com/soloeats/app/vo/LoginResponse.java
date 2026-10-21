package com.soloeats.app.vo;

import java.util.Set;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresIn,
        Set<String> roles
) {
}
