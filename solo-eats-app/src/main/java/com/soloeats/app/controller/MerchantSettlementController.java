package com.soloeats.app.controller;

import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.settlement.service.SettlementService;
import com.soloeats.settlement.service.view.SettlementDetailResult;
import com.soloeats.settlement.service.view.SettlementListResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequireRole(Roles.MERCHANT)
@RestController
@RequestMapping("/api/v1/merchant/settlements")
@RequiredArgsConstructor
public class MerchantSettlementController {

    private final SettlementService settlementService;
    private final PermissionGuard permissionGuard;

    @GetMapping
    public ApiResponse<SettlementListResult> list(
            @RequestParam Long merchantId,
            @RequestParam(required = false) Integer status) {
        permissionGuard.assertMerchant(merchantId);
        return ApiResponse.ok(settlementService.listMerchantBills(merchantId, status));
    }

    @GetMapping("/{billId}")
    public ApiResponse<SettlementDetailResult> detail(
            @PathVariable Long billId,
            @RequestParam Long merchantId) {
        permissionGuard.assertMerchant(merchantId);
        return ApiResponse.ok(settlementService.getBillDetail(merchantId, billId));
    }
}
