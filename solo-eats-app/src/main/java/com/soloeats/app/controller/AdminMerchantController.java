package com.soloeats.app.controller;

import com.soloeats.app.dto.AdminMerchantRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.merchant.service.MerchantAdminService;
import com.soloeats.merchant.service.command.AuditMerchantCommand;
import com.soloeats.merchant.service.view.MerchantDetailView;
import com.soloeats.merchant.service.view.MerchantView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台侧商家招商。权限码 mine 由 t_sys_menu 种子（V7）授予超管/招商运营/财务。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminMerchantController {

    private final MerchantAdminService merchantAdminService;

    @RequirePermission("merchant:list")
    @GetMapping("/merchants")
    public ApiResponse<PageResult<MerchantView>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(merchantAdminService.page(
                status, keyword, categoryId, pageNum, pageSize));
    }

    @RequirePermission("merchant:detail")
    @GetMapping("/merchants/{id}")
    public ApiResponse<MerchantDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(merchantAdminService.detail(id));
    }

    /**
     * 通过入驻审核。recordParams=false：请求体含 initialPassword，禁止落操作日志。
     */
    @RequirePermission("merchant:audit")
    // 防重窗口只覆盖并发提交；窗口外重复调用由 service 的「已通过直接返回详情」兜底
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "商家管理", action = "审核通过", recordParams = false)
    @PostMapping("/merchants/{id}/approve")
    public ApiResponse<MerchantDetailView> approve(
            @PathVariable Long id,
            @Valid @RequestBody AdminMerchantRequest request) {
        UserPrincipal operator = AuthContext.require();
        return ApiResponse.ok(merchantAdminService.approve(id, new AuditMerchantCommand(
                request.commissionRate(),
                request.merchantLevel(),
                request.adminUsername(),
                request.initialPassword(),
                request.remark(),
                operator.userId(),
                operatorName(operator))));
    }

    @RequirePermission("merchant:audit")
    @OperationLog(module = "商家管理", action = "审核驳回")
    @PostMapping("/merchants/{id}/reject")
    public ApiResponse<MerchantDetailView> reject(
            @PathVariable Long id,
            @Valid @RequestBody AdminMerchantRequest.RejectRequest request) {
        UserPrincipal operator = AuthContext.require();
        return ApiResponse.ok(merchantAdminService.reject(
                id, request.remark(), operator.userId(), operatorName(operator)));
    }

    @RequirePermission("merchant:update")
    @OperationLog(module = "商家管理", action = "启用停用")
    @PutMapping("/merchants/{id}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminMerchantRequest.StatusRequest request) {
        UserPrincipal operator = AuthContext.require();
        merchantAdminService.updateStatus(
                id, request.status(), operator.userId(), operatorName(operator));
        return ApiResponse.ok();
    }

    @RequirePermission("merchant:update")
    @OperationLog(module = "商家管理", action = "调整抽佣")
    @PutMapping("/merchants/{id}/commission")
    public ApiResponse<Void> updateCommission(
            @PathVariable Long id,
            @Valid @RequestBody AdminMerchantRequest.CommissionRequest request) {
        merchantAdminService.updateCommission(id, request.commissionRate());
        return ApiResponse.ok();
    }

    @RequirePermission("merchant:audit")
    @OperationLog(module = "商家管理", action = "结算账户审核")
    @PutMapping("/merchant-accounts/{id}/audit")
    public ApiResponse<Void> auditSettlementAccount(
            @PathVariable Long id,
            @Valid @RequestBody AdminMerchantRequest.AccountAuditRequest request) {
        merchantAdminService.auditSettlementAccount(id, request.status());
        return ApiResponse.ok();
    }

    private String operatorName(UserPrincipal operator) {
        return operator.username() == null ? "ADMIN" : operator.username();
    }
}
