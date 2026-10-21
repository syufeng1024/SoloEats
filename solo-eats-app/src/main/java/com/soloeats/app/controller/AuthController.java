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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 本地联调用模拟登录。生产环境应替换为手机号/账号密码或第三方登录。
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtils jwtUtils;

    @RateLimit(key = "auth-login", limit = 10, windowSeconds = 60, dimension = RateLimitDimension.IP)
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String role = request.role();
        UserPrincipal principal;

        if (Roles.C_USER.equals(role)) {
            if (request.userId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "C_USER 必须提供 userId");
            }
            principal = new UserPrincipal(request.userId(), null, null, null, Set.of(role));
        } else if (Roles.MERCHANT.equals(role)) {
            if (request.merchantId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "MERCHANT 必须提供 merchantId");
            }
            principal = new UserPrincipal(null, request.merchantId(), request.shopId(), null, Set.of(role));
        } else if (Roles.RIDER.equals(role)) {
            if (request.riderId() == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "RIDER 必须提供 riderId");
            }
            principal = new UserPrincipal(null, null, null, request.riderId(), Set.of(role));
        } else if (Roles.ADMIN.equals(role)) {
            principal = new UserPrincipal(
                    request.userId() == null ? 0L : request.userId(),
                    null,
                    null,
                    null,
                    Set.of(role)
            );
        } else {
            throw new BizException(ErrorCode.PARAM_INVALID, "role 不合法");
        }

        String token = jwtUtils.issue(principal);
        return ApiResponse.ok(new LoginResponse(
                token,
                "Bearer",
                jwtUtils.expireSeconds(),
                principal.roles()
        ));
    }
}
