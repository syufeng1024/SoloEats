package com.soloeats.common.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    /** 与 /auth/logout 约定一致：登出时写 jwt:blacklist:{jti}，TTL 取令牌剩余有效期。 */
    public static final String BLACKLIST_KEY_PREFIX = "jwt:blacklist:";

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * WS 握手不走 Bearer 头：{@code /ws?token=...} 由 MessageWebSocketHandler 自行校验。
     * 仅放行握手路径，业务接口（/api/**）鉴权不变。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && ("/ws".equals(uri) || uri.startsWith("/ws/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtils.parseClaims(header.substring(7));
                if (isBlacklisted(claims.getId())) {
                    writeUnauthorized(response);
                    return;
                }
                AuthContext.set(jwtUtils.toPrincipal(claims));
            } catch (Exception ex) {
                writeUnauthorized(response);
                return;
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    /**
     * Redis 不可用时放行并告警：黑名单只影响主动登出的令牌，
     * 不能因缓存故障让全部请求 401；未登出的令牌仍受自身有效期约束。
     */
    private boolean isBlacklisted(String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti));
        } catch (Exception ex) {
            log.warn("jwt blacklist check failed jti={} reason={}", jti, ex.getMessage());
            return false;
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.fail(ErrorCode.AUTH_UNAUTHORIZED.getCode(), ErrorCode.AUTH_UNAUTHORIZED.getMessage()));
    }
}
