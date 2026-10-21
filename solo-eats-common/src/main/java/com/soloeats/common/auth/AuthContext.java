package com.soloeats.common.auth;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;

public final class AuthContext {

    private static final ThreadLocal<UserPrincipal> PRINCIPAL = new ThreadLocal<>();

    private AuthContext() {
    }

    public static UserPrincipal current() {
        return PRINCIPAL.get();
    }

    public static UserPrincipal require() {
        UserPrincipal principal = PRINCIPAL.get();
        if (principal == null) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return principal;
    }

    public static void set(UserPrincipal principal) {
        PRINCIPAL.set(principal);
    }

    public static void clear() {
        PRINCIPAL.remove();
    }
}
