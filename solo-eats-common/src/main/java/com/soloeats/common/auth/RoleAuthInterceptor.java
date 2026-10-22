package com.soloeats.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class RoleAuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return true;
        }

        UserPrincipal principal = AuthContext.current();
        if (principal == null) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED);
            return false;
        }

        boolean allowed = Arrays.stream(requireRole.value())
                .anyMatch(required -> matches(principal, required));
        if (!allowed) {
            write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.AUTH_FORBIDDEN);
            return false;
        }
        return true;
    }

    /**
     * 平台后台细分为 8 个角色（ADMIN、ADMIN_OPERATION、ADMIN_PRODUCT …，规格书 3.1），
     * 类级 {@code @RequireRole(Roles.ADMIN)} 不应把其余 7 个平台角色挡在门外。
     * 权限码仍由 PermissionInterceptor 精确校验，这里只放宽角色粗筛。
     */
    private boolean matches(UserPrincipal principal, String required) {
        if (principal.hasRole(required)) {
            return true;
        }
        if (!Roles.ADMIN.equals(required) || principal.roles() == null) {
            return false;
        }
        return principal.roles().stream()
                .anyMatch(role -> role != null && role.startsWith(Roles.ADMIN));
    }

    private void write(HttpServletResponse response, int status, ErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }
}
