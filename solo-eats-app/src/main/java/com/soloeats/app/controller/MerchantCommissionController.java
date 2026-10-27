package com.soloeats.app.controller;

import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.settlement.service.CommissionService;
import com.soloeats.settlement.service.view.CommissionRecordView;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 商家自查抽佣明细（按需导出核对用）。
 *
 * <p>merchantId 一律从登录态解析，不接受请求参数传入，避免越权查看其他商家抽佣。
 */
@RestController
@RequiredArgsConstructor
public class MerchantCommissionController {

    private final CommissionService commissionService;
    private final PermissionGuard permissionGuard;

    @RequireRole(Roles.MERCHANT)
    @GetMapping("/api/v1/merchant/commission-details")
    public ApiResponse<PageResult<CommissionRecordView>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        Long merchantId = permissionGuard.requireMerchantId();
        return ApiResponse.ok(commissionService.pageRecords(
                merchantId, categoryId, dateFrom, dateTo, pageNum, pageSize));
    }
}
