package com.soloeats.app.controller;

import com.soloeats.app.dto.PayoutAuditRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.log.OperationLog;
import com.soloeats.settlement.service.PayoutService;
import com.soloeats.settlement.service.view.PayoutView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 平台侧提现管理：列表、审核、模拟打款、到账登记。
 * 权限码由 V19 种入 t_sys_menu（id 108~112）并授予超管。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/payouts")
@RequiredArgsConstructor
public class AdminPayoutController {

    private final PayoutService payoutService;

    @RequirePermission("settlement:payout:list")
    @GetMapping
    public ApiResponse<PageResult<PayoutView>> list(
            @RequestParam(required = false) Integer subjectType,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(payoutService.adminPage(
                subjectType, subjectId, status, dateFrom, dateTo, pageNum, pageSize));
    }

    @RequirePermission("settlement:payout:audit")
    @OperationLog(module = "settlement", action = "payoutAudit")
    @PostMapping("/{id}/audit")
    public ApiResponse<Void> audit(@PathVariable("id") Long id,
                                   @Valid @RequestBody PayoutAuditRequest request) {
        payoutService.audit(id, request.pass(), request.remark(),
                AuthContext.require().userId());
        return ApiResponse.ok();
    }

    @RequirePermission("settlement:payout:pay")
    @OperationLog(module = "settlement", action = "payoutPay")
    @PostMapping("/{id}/pay")
    public ApiResponse<Void> pay(@PathVariable("id") Long id) {
        payoutService.pay(id);
        return ApiResponse.ok();
    }

    @RequirePermission("settlement:payout:pay")
    @OperationLog(module = "settlement", action = "payoutArrive")
    @PostMapping("/{id}/arrive")
    public ApiResponse<Void> arrive(@PathVariable("id") Long id) {
        payoutService.arrive(id);
        return ApiResponse.ok();
    }
}
