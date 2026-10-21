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

        boolean allowed = Arrays.stream(requireRole.value()).anyMatch(principal::hasRole);
        if (!allowed) {
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
