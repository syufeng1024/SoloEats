package com.soloeats.app.controller;

import com.soloeats.app.dto.SmsCodeRequest;
import com.soloeats.app.dto.SmsLoginRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.ratelimit.RateLimit;
import com.soloeats.common.ratelimit.RateLimitDimension;
import com.soloeats.member.domain.enums.SmsScene;
import com.soloeats.member.service.MemberAuthService;
import com.soloeats.member.service.command.SmsLoginCommand;
import com.soloeats.member.service.view.SmsLoginResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端消费者登录入口。与 {@code /api/v1/auth/login}（平台/商家/骑手）互不影响：
 * 本接口只签发 roles=[C_USER] 的 token。
 */
@RestController
@RequestMapping("/api/v1/c/auth")
@RequiredArgsConstructor
public class ConsumerAuthController {

    private final MemberAuthService memberAuthService;

    @PostMapping("/sms-code")
    public ApiResponse<Void> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        memberAuthService.sendSmsCode(request.phone(), SmsScene.fromCode(request.scene()));
        return ApiResponse.ok();
    }

    /** 登录接口按 IP 限流 10 次/分钟；手机号维度的验证码限流在 service 内完成。 */
    @RateLimit(key = "member-login", limit = 10, windowSeconds = 60, dimension = RateLimitDimension.IP)
    @PostMapping("/login")
    public ApiResponse<SmsLoginResult> login(@Valid @RequestBody SmsLoginRequest request) {
        return ApiResponse.ok(memberAuthService.loginBySms(
                new SmsLoginCommand(request.phone(), request.code())));
    }
}
