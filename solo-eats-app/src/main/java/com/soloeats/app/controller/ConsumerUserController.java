package com.soloeats.app.controller;

import com.soloeats.app.dto.AgreementConfirmRequest;
import com.soloeats.app.dto.ChangePhoneRequest;
import com.soloeats.app.dto.UpdateProfileRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.member.service.MemberAgreementService;
import com.soloeats.member.service.MemberProfileService;
import com.soloeats.member.service.command.AgreementConfirmCommand;
import com.soloeats.member.service.command.ChangePhoneCommand;
import com.soloeats.member.service.command.UpdateProfileCommand;
import com.soloeats.member.service.view.AgreementStatusView;
import com.soloeats.member.service.view.MemberView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端「我的」：个人资料、手机号换绑、注销、隐私协议。
 * 登录入口在 {@link ConsumerAuthController}（/api/v1/c/auth/**），此处不重复映射。
 */
@RestController
@RequestMapping("/api/v1/c/users")
@RequiredArgsConstructor
public class ConsumerUserController {

    private final MemberProfileService memberProfileService;
    private final MemberAgreementService memberAgreementService;

    @RequireRole(Roles.C_USER)
    @GetMapping("/me")
    public ApiResponse<MemberView> me() {
        return ApiResponse.ok(memberProfileService.getProfile(currentUserId()));
    }

    @RequireRole(Roles.C_USER)
    @PutMapping("/me")
    public ApiResponse<MemberView> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(memberProfileService.updateProfile(currentUserId(),
                new UpdateProfileCommand(
                        request.nickname(),
                        request.avatarUrl(),
                        request.gender(),
                        request.birthday())));
    }

    @RequireRole(Roles.C_USER)
    @PutMapping("/me/phone")
    public ApiResponse<MemberView> changePhone(@Valid @RequestBody ChangePhoneRequest request) {
        return ApiResponse.ok(memberProfileService.changePhone(currentUserId(),
                new ChangePhoneCommand(request.newPhone(), request.code())));
    }

    @RequireRole(Roles.C_USER)
    @PostMapping("/me/cancel")
    public ApiResponse<Void> applyCancel() {
        memberProfileService.applyCancel(currentUserId());
        return ApiResponse.ok();
    }

    /** 冷静期内撤回注销申请（任务卡接口 7 的反向操作）。 */
    @RequireRole(Roles.C_USER)
    @PutMapping("/me/cancel")
    public ApiResponse<Void> cancelCancel() {
        memberProfileService.cancelCancel(currentUserId());
        return ApiResponse.ok();
    }

    @RequireRole(Roles.C_USER)
    @GetMapping("/agreement")
    public ApiResponse<AgreementStatusView> agreement() {
        return ApiResponse.ok(memberAgreementService.getStatus(currentUserId()));
    }

    @RequireRole(Roles.C_USER)
    @PostMapping("/agreement/confirm")
    public ApiResponse<AgreementStatusView> confirmAgreement(
            @Valid @RequestBody AgreementConfirmRequest request) {
        return ApiResponse.ok(memberAgreementService.confirm(currentUserId(),
                new AgreementConfirmCommand(request.agreementVersion(), request.agree())));
    }

    /** userId 只取令牌，不接受请求体传入。 */
    private Long currentUserId() {
        return AuthContext.require().userId();
    }
}
