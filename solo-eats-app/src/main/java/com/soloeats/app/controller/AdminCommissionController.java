package com.soloeats.app.controller;

import com.soloeats.app.dto.CommissionRuleSaveRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.settlement.service.CommissionService;
import com.soloeats.settlement.service.view.CommissionRecordView;
import com.soloeats.settlement.service.view.CommissionResolveResult;
import com.soloeats.settlement.service.view.CommissionRuleView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 平台抽佣规则与抽佣明细：规则 CRUD、命中试算、明细分页。
 * 权限码由 V27 种入 t_sys_menu（id 142~144），挂在「资金中心」parent_id = 8 下。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCommissionController {

    private final CommissionService commissionService;

    @RequirePermission("settlement:commission:list")
    @GetMapping("/commission-rules")
    public ApiResponse<PageResult<CommissionRuleView>> pageRules(
            @RequestParam(required = false) Integer ruleScope,
            @RequestParam(required = false) Long refId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(commissionService.pageRules(
                ruleScope, refId, status, pageNum, pageSize));
    }

    /**
     * 规则命中试算：不落库，只回命中结果，用于后台配置联调。
     * 无任何规则命中时返回内置默认费率（0.0600）且 ruleId 为 null。
     */
    @RequirePermission("settlement:commission:list")
    @GetMapping("/commission-rules/resolve")
    public ApiResponse<CommissionResolveResult> resolve(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long promotionId,
            @RequestParam(required = false) Long amount) {
        return ApiResponse.ok(commissionService.resolve(merchantId, categoryId, promotionId, amount));
    }

    @RequirePermission("settlement:commission:list")
    @GetMapping("/commission-rules/{id}")
    public ApiResponse<CommissionRuleView> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(commissionService.get(id));
    }

    @RequirePermission("settlement:commission:save")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "settlement", action = "createCommissionRule")
    @PostMapping("/commission-rules")
    public ApiResponse<CommissionRuleView> create(
            @Valid @RequestBody CommissionRuleSaveRequest request) {
        return ApiResponse.ok(commissionService.create(request.toCommand()));
    }

    @RequirePermission("settlement:commission:save")
    @OperationLog(module = "settlement", action = "updateCommissionRule")
    @PutMapping("/commission-rules/{id}")
    public ApiResponse<CommissionRuleView> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody CommissionRuleSaveRequest request) {
        return ApiResponse.ok(commissionService.update(id, request.toCommand()));
    }

    @RequirePermission("settlement:commission:list")
    @GetMapping("/commission-records")
    public ApiResponse<PageResult<CommissionRecordView>> pageRecords(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(commissionService.pageRecords(
                merchantId, categoryId, dateFrom, dateTo, pageNum, pageSize));
    }
}
