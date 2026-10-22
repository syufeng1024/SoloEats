package com.soloeats.system.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 把当前请求的租户写入 {@link TenantContext}，请求结束必须清理，避免线程复用串租户。
 *
 * <p>取值优先级：登录身份里的 tenantId → 请求头 {@code X-Tenant-Id}（仅 ADMIN）→ 默认租户 1。
 * 必须排在 {@code JwtAuthFilter}（HIGHEST_PRECEDENCE + 10）之后，否则读不到 AuthContext。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class TenantFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        UserPrincipal principal = AuthContext.current();
        Long headerTenantId = null;
        if (principal != null && principal.hasRole(Roles.ADMIN)) {
            String header = request.getHeader(TENANT_HEADER);
            if (StringUtils.hasText(header)) {
                headerTenantId = parseTenantId(header.trim());
                if (headerTenantId == null) {
                    writeBadRequest(response, TENANT_HEADER + " 必须是正整数");
                    return;
                }
            }
        }

        Long tenantId = headerTenantId;
        if (tenantId == null && principal != null && principal.tenantId() != null) {
            tenantId = principal.tenantId();
        }
        if (tenantId == null) {
            tenantId = TenantContext.DEFAULT_TENANT_ID;
        }

        TenantContext.set(tenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Long parseTenantId(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0L ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void writeBadRequest(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), message));
    }
}
