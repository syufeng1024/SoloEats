package com.soloeats.app.controller;

import com.soloeats.app.dto.MerchantProfileRequest;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.merchant.service.MerchantProfileService;
import com.soloeats.merchant.service.command.SaveSettlementAccountCommand;
import com.soloeats.merchant.service.command.UpdateMerchantProfileCommand;
import com.soloeats.merchant.service.view.MerchantDetailView;
import com.soloeats.merchant.service.view.SettlementAccountView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家自助端。merchantId 一律取登录令牌，不接受请求参数覆盖。
 *
 * <p>真实商家账号绑定 t_sys_role.role_code=MERCHANT_MANAGER，而 mock 登录签发的是 MERCHANT，
 * 两个角色码都放行，避免账号密码登录后被 403。
 */
@RequireRole({Roles.MERCHANT, "MERCHANT_MANAGER"})
@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class MerchantProfileController {

    private final MerchantProfileService merchantProfileService;
    private final PermissionGuard permissionGuard;

    @GetMapping("/profile")
    public ApiResponse<MerchantDetailView> profile() {
        return ApiResponse.ok(merchantProfileService.profile(permissionGuard.requireMerchantId()));
    }

    @PutMapping("/profile")
    public ApiResponse<MerchantDetailView> updateProfile(
            @Valid @RequestBody MerchantProfileRequest request) {
        Long merchantId = permissionGuard.requireMerchantId();
        return ApiResponse.ok(merchantProfileService.updateProfile(merchantId,
                new UpdateMerchantProfileCommand(
                        request.contactName(),
                        request.contactPhone(),
                        request.contactEmail(),
                        request.province(),
                        request.city(),
                        request.district(),
                        request.address())));
    }

    @GetMapping("/settlement-accounts")
    public ApiResponse<List<SettlementAccountView>> listSettlementAccounts() {
        return ApiResponse.ok(merchantProfileService.listSettlementAccounts(
                permissionGuard.requireMerchantId()));
    }

    @PostMapping("/settlement-accounts")
    public ApiResponse<SettlementAccountView> createSettlementAccount(
            @Valid @RequestBody MerchantProfileRequest.SettlementAccountRequest request) {
        Long merchantId = permissionGuard.requireMerchantId();
        return ApiResponse.ok(merchantProfileService.createSettlementAccount(merchantId,
                new SaveSettlementAccountCommand(
                        request.accountName(),
                        request.bankName(),
                        request.bankBranch(),
                        request.bankAccountNo(),
                        request.alipayAccount(),
                        request.contractFileId(),
                        request.effectiveFrom(),
                        request.effectiveTo())));
    }
}
