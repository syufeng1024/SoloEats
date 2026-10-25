package com.soloeats.app.controller;

import com.soloeats.app.dto.PayoutApplyRequest;
import com.soloeats.app.security.CurrentReceiver;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.settlement.service.PayoutService;
import com.soloeats.settlement.service.view.PayoutView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家端与骑手端提现。subjectType/subjectId 一律从登录态解析，不接受请求体传入：
 * 商家取 PermissionGuard.requireMerchantId()（t_merchant.id），
 * 骑手取 CurrentReceiver#receiverId（t_rider.id，由登录账号 userId 反查）。
 */
@RestController
@RequiredArgsConstructor
public class MerchantPayoutController {

    private final PayoutService payoutService;
    private final PermissionGuard permissionGuard;
    private final CurrentReceiver currentReceiver;

    @RequireRole(Roles.MERCHANT)
    @PostMapping("/api/v1/merchant/payouts")
    public ApiResponse<PayoutView> applyAsMerchant(@Valid @RequestBody PayoutApplyRequest request) {
        Long merchantId = permissionGuard.requireMerchantId();
        return ApiResponse.ok(payoutService.apply(
                PayoutService.SUBJECT_TYPE_MERCHANT, merchantId, request.amount(), null));
    }

    @RequireRole(Roles.MERCHANT)
    @GetMapping("/api/v1/merchant/payouts")
    public ApiResponse<PageResult<PayoutView>> listAsMerchant(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        Long merchantId = permissionGuard.requireMerchantId();
        return ApiResponse.ok(payoutService.pageMine(
                PayoutService.SUBJECT_TYPE_MERCHANT, merchantId, status, pageNum, pageSize));
    }

    @RequireRole(Roles.RIDER)
    @PostMapping("/api/v1/rider/payouts")
    public ApiResponse<PayoutView> applyAsRider(@Valid @RequestBody PayoutApplyRequest request) {
        Long riderId = currentReceiver.receiverId(AuthContext.require());
        return ApiResponse.ok(payoutService.apply(
                PayoutService.SUBJECT_TYPE_RIDER, riderId, request.amount(), null));
    }

    @RequireRole(Roles.RIDER)
    @GetMapping("/api/v1/rider/payouts")
    public ApiResponse<PageResult<PayoutView>> listAsRider(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        Long riderId = currentReceiver.receiverId(AuthContext.require());
        return ApiResponse.ok(payoutService.pageMine(
                PayoutService.SUBJECT_TYPE_RIDER, riderId, status, pageNum, pageSize));
    }
}
