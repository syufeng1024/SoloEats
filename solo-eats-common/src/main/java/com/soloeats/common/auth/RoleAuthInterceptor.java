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
     * 角色族（role family）匹配：token 角色码与 required 精确相等，或以 {@code required + "_"} 开头。
     * <p>V4__system_rbac.sql 的种子角色是细粒度角色码：平台侧 ADMIN_OPERATION / ADMIN_PRODUCT …，
     * 商家侧 MERCHANT_MANAGER / MERCHANT_CLERK，骑手侧只有 RIDER。Controller 上声明的是粗粒度族码
     * {@link Roles#ADMIN} / {@link Roles#MERCHANT}，因此 {@code @RequireRole(MERCHANT)} 必须放行
     * MERCHANT_MANAGER、MERCHANT_CLERK，否则商家账号会在归属校验之前被 10004 拦下。
     * <p>只认 {@code required + "_"} 前缀，不做包含匹配（MERCHANT 不会误匹配 AMERCHANT），
     * 也不放行 {@code required.startsWith(role)}；RIDER / C_USER 种子里没有细分，仍只精确匹配。
     * 权限码仍由 PermissionInterceptor 精确校验，这里只放宽角色粗筛。
     */
    private boolean matches(UserPrincipal principal, String required) {
        if (principal.roles() == null || required == null) {
            return false;
        }
        for (String role : principal.roles()) {
            if (role == null) {
                continue;
            }
            if (role.equals(required) || role.startsWith(required + "_")) {
                return true;
            }
        }
        return false;
    }

    private void write(HttpServletResponse response, int status, ErrorCode errorCode) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.fail(errorCode.getCode(), errorCode.getMessage()));
    }
}
