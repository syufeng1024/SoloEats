package com.soloeats.app.controller;

import com.soloeats.app.dto.LoginRequest;
import com.soloeats.app.vo.LoginResponse;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.JwtUtils;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.ratelimit.RateLimit;
import com.soloeats.common.ratelimit.RateLimitDimension;
import com.soloeats.system.service.AuthService;
import com.soloeats.system.service.SysLogService;
import com.soloeats.system.service.command.PasswordLoginCommand;
import com.soloeats.system.service.view.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Set;

/**
 * 登录入口，支持两种模式：
 * 1. 账号密码：委托 AuthService 查 t_sys_user + bcrypt 校验 + 签发 JWT；
 * 2. 旧契约模拟登录：{role,userId,merchantId,shopId,riderId}，由 app.auth.mock-login-enabled 控制（默认开启）。
 *
 * <p>登录成功/失败都写 t_sys_login_log；同一 IP 1 分钟失败超过 5 次锁定 10 分钟（Redis，失败放行）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String FAIL_KEY_PREFIX = "auth:login:fail:";
    private static final String LOCK_KEY_PREFIX = "auth:login:lock:";

    private static final int MAX_FAILURES_PER_WINDOW = 5;
    private static final Duration FAIL_WINDOW = Duration.ofMinutes(1);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private static final int LOGIN_TYPE_PASSWORD = 1;
    private static final int LOGIN_TYPE_MOCK = 3;

    private final JwtUtils jwtUtils;
    private final AuthService authService;
    private final SysLogService sysLogService;
    private final StringRedisTemplate redisTemplate;
    private final boolean mockLoginEnabled;

    public AuthController(JwtUtils jwtUtils,
                          AuthService authService,
                          SysLogService sysLogService,
                          StringRedisTemplate redisTemplate,
                          @Value("${app.auth.mock-login-enabled:true}") boolean mockLoginEnabled) {
        this.jwtUtils = jwtUtils;
        this.authService = authService;
        this.sysLogService = sysLogService;
        this.redisTemplate = redisTemplate;
        this.mockLoginEnabled = mockLoginEnabled;
    }

    @RateLimit(key = "auth-login", limit = 10, windowSeconds = 60, dimension = RateLimitDimension.IP)
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        String ip = clientIp(httpRequest);
        String ua = truncate(httpRequest.getHeader("User-Agent"), 512);

        if (isLocked(ip)) {
            int loginType = hasText(request.username()) || hasText(request.password())
                    ? LOGIN_TYPE_PASSWORD : LOGIN_TYPE_MOCK;
            sysLogService.recordLogin(null, null, request.username(), loginType,
                    false, ErrorCode.AUTH_ACCOUNT_LOCKED.getMessage(), ip, ua);
            throw new BizException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }

        if (hasText(request.username()) || hasText(request.password())) {
            return passwordLogin(request, ip, ua);
        }
        return mockLogin(request, ip, ua);
    }

    private ApiResponse<LoginResponse> passwordLogin(LoginRequest request, String ip, String ua) {
        String username = request.username() == null ? null : request.username().trim();
        try {
            LoginResult result = authService.login(
                    new PasswordLoginCommand(request.username(), request.password()));
            UserPrincipal principal = jwtUtils.parse(result.token());
            sysLogService.recordLogin(principal.tenantId(), principal.userId(), principal.username(),
                    LOGIN_TYPE_PASSWORD, true, null, ip, ua);
            clearFailures(ip);
            return ApiResponse.ok(new LoginResponse(
                    result.token(),
                    result.tokenType(),
                    result.expiresIn(),
                    result.roles(),
                    result.permissions()));
        } catch (BizException ex) {
            sysLogService.recordLogin(null, null, username, LOGIN_TYPE_PASSWORD,
                    false, ex.getMessage(), ip, ua);
            registerFailure(ip);
            throw ex;
        }
    }

    private ApiResponse<LoginResponse> mockLogin(LoginRequest request, String ip, String ua) {
        if (!mockLoginEnabled) {
            BizException ex = new BizException(ErrorCode.PARAM_INVALID,
                    "模拟登录已关闭，请使用 username/password 登录");
            sysLogService.recordLogin(null, null, request.role(), LOGIN_TYPE_MOCK,
                    false, ex.getMessage(), ip, ua);
            throw ex;
        }
        try {
            LoginResponse response = mockLoginResponse(request);
            UserPrincipal principal = jwtUtils.parse(response.token());
            sysLogService.recordLogin(principal.tenantId(), principal.userId(), request.role(),
                    LOGIN_TYPE_MOCK, true, null, ip, ua);
            clearFailures(ip);
            return ApiResponse.ok(response);
        } catch (BizException ex) {
            sysLogService.recordLogin(null, null, request.role(), LOGIN_TYPE_MOCK,
                    false, ex.getMessage(), ip, ua);
            registerFailure(ip);
            throw ex;
        }
    }

    /**
     * 旧契约分支：保持原有 role → UserPrincipal 的构造方式不变。
     */
    private LoginResponse mockLoginResponse(LoginRequest request) {
        String role = request.role();
        UserPrincipal principal;

        if (Roles.C_USER.equals(role)) {
            if (request.userId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "C_USER 必须提供 userId");
            }
            principal = new UserPrincipal(null, request.userId(), null, null, null, null, Set.of(role));
        } else if (Roles.MERCHANT.equals(role)) {
            if (request.merchantId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "MERCHANT 必须提供 merchantId");
            }
            principal = new UserPrincipal(null, null, null, request.merchantId(), request.shopId(), null, Set.of(role));
        } else if (Roles.RIDER.equals(role)) {
            if (request.riderId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "RIDER 必须提供 riderId");
            }
            principal = new UserPrincipal(null, null, null, null, null, request.riderId(), Set.of(role));
        } else if (Roles.ADMIN.equals(role)) {
            principal = new UserPrincipal(
                    null,
                    request.userId() == null ? 0L : request.userId(),
                    null,
                    null,
                    null,
                    null,
                    Set.of(role)
            );
        } else {
            throw new BizException(ErrorCode.PARAM_INVALID, "role 不合法");
        }

        String token = jwtUtils.issue(principal);
        return new LoginResponse(
                token,
                "Bearer",
                jwtUtils.expireSeconds(),
                principal.roles(),
                Set.of()
        );
    }

    private boolean isLocked(String ip) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(LOCK_KEY_PREFIX + ip));
        } catch (RuntimeException ex) {
            // Redis 故障时放行，锁定只是防护手段，不能阻断全部登录
            log.warn("login lock check failed ip={} reason={}", ip, ex.getMessage());
            return false;
        }
    }

    private void registerFailure(String ip) {
        try {
            String key = FAIL_KEY_PREFIX + ip;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, FAIL_WINDOW);
            }
            if (count != null && count > MAX_FAILURES_PER_WINDOW) {
                redisTemplate.opsForValue().set(LOCK_KEY_PREFIX + ip, "1", LOCK_DURATION);
                log.warn("login locked ip={} failuresInWindow={}", ip, count);
            }
        } catch (RuntimeException ex) {
            log.warn("login failure counter unavailable ip={} reason={}", ip, ex.getMessage());
        }
    }

    private void clearFailures(String ip) {
        try {
            redisTemplate.delete(FAIL_KEY_PREFIX + ip);
        } catch (RuntimeException ex) {
            log.warn("login failure counter clear failed ip={} reason={}", ip, ex.getMessage());
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
