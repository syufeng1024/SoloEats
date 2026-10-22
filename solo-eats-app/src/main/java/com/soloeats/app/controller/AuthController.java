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
import com.soloeats.system.service.command.PasswordLoginCommand;
import com.soloeats.system.service.view.LoginResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 登录入口，支持两种模式：
 * 1. 账号密码：委托 AuthService 查 t_sys_user + bcrypt 校验 + 签发 JWT；
 * 2. 旧契约模拟登录：{role,userId,merchantId,shopId,riderId}，由 app.auth.mock-login-enabled 控制（默认开启）。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtUtils jwtUtils;
    private final AuthService authService;
    private final boolean mockLoginEnabled;

    public AuthController(JwtUtils jwtUtils,
                          AuthService authService,
                          @Value("${app.auth.mock-login-enabled:true}") boolean mockLoginEnabled) {
        this.jwtUtils = jwtUtils;
        this.authService = authService;
        this.mockLoginEnabled = mockLoginEnabled;
    }

    @RateLimit(key = "auth-login", limit = 10, windowSeconds = 60, dimension = RateLimitDimension.IP)
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        if (hasText(request.username()) || hasText(request.password())) {
            LoginResult result = authService.login(
                    new PasswordLoginCommand(request.username(), request.password()));
            return ApiResponse.ok(new LoginResponse(
                    result.token(),
                    result.tokenType(),
                    result.expiresIn(),
                    result.roles(),
                    result.permissions()));
        }

        if (!mockLoginEnabled) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "模拟登录已关闭，请使用 username/password 登录");
        }
        return ApiResponse.ok(mockLogin(request));
    }

    /**
     * 旧契约分支：保持原有 role → UserPrincipal 的构造方式不变。
     */
    private LoginResponse mockLogin(LoginRequest request) {
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
