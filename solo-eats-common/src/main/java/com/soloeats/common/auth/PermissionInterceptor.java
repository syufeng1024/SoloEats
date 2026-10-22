package com.soloeats.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 按 {@link RequirePermission} 声明的权限码做接口级校验。
 * 注册在 {@link RoleAuthInterceptor} 之后：先按角色粗筛，再按权限码细筛。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionCache permissionCache;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission requirePermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (requirePermission == null) {
            requirePermission = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (requirePermission == null) {
            return true;
        }

        UserPrincipal principal = AuthContext.current();
        if (principal == null) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED);
            return false;
        }

        String permission = requirePermission.value();
        if (permission == null || permission.isBlank()) {
            return true;
        }

        // 旧契约 mock 登录签发的 token 没有账号身份（username 为空），
        // 不写权限缓存，仅受 @RequireRole 约束；账号密码登录的 token 才校验权限码。
        if (principal.username() == null) {
            return true;
        }

        if (principal.userId() == null || !permissionCache.has(principal.userId(), permission)) {
            log.warn("permission denied userId={} permission={}", principal.userId(), permission);
            write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.AUTH_FORBIDDEN);
            return false;
        }
        return true;
    }

    private void write(HttpServletResponse response, int status, ErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }
}
