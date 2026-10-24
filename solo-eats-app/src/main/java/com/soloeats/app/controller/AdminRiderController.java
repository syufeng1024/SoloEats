package com.soloeats.app.controller;

import com.soloeats.app.dto.AuditCertificationRequest;
import com.soloeats.app.dto.SaveDispatchConfigRequest;
import com.soloeats.app.dto.UpdateRiderLimitsRequest;
import com.soloeats.app.dto.UpdateRiderStatusRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.delivery.service.DispatchConfigService;
import com.soloeats.delivery.service.RiderService;
import com.soloeats.delivery.service.command.RiderQuery;
import com.soloeats.delivery.service.command.SaveDispatchConfigCommand;
import com.soloeats.delivery.service.view.DispatchConfigView;
import com.soloeats.delivery.service.view.RiderDetailView;
import com.soloeats.delivery.service.view.RiderView;
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
 * 平台侧骑手管理 + 派单策略配置（T07 阶段A）。
 * 权限码由 V14 种入 t_sys_menu（id 92~97）并授予超管。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminRiderController {

    private final RiderService riderService;
    private final DispatchConfigService dispatchConfigService;

    @RequirePermission("delivery:rider:list")
    @GetMapping("/riders")
    public ApiResponse<PageResult<RiderView>> list(
            @RequestParam(required = false) Integer certStatus,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(riderService.adminPage(
                new RiderQuery(certStatus, status, keyword), pageNum, pageSize));
    }

    @RequirePermission("delivery:rider:detail")
    @GetMapping("/riders/{id}")
    public ApiResponse<RiderDetailView> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(riderService.adminDetail(id));
    }

    @RequirePermission("delivery:rider:audit")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "骑手管理", action = "认证审核")
    @PostMapping("/riders/{id}/certification/audit")
    public ApiResponse<Void> audit(@PathVariable("id") Long id,
                                   @Valid @RequestBody AuditCertificationRequest request) {
        Long operatorId = AuthContext.require().userId();
        riderService.audit(id, request.status(), request.remark(), operatorId);
        return ApiResponse.ok();
    }

    @RequirePermission("delivery:rider:update")
    @OperationLog(module = "骑手管理", action = "启用停用")
    @PutMapping("/riders/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable("id") Long id,
                                          @Valid @RequestBody UpdateRiderStatusRequest request) {
        riderService.updateStatus(id, request.status());
        return ApiResponse.ok();
    }

    @RequirePermission("delivery:rider:update")
    @OperationLog(module = "骑手管理", action = "调整接单限制")
    @PutMapping("/riders/{id}/limits")
    public ApiResponse<Void> updateLimits(@PathVariable("id") Long id,
                                          @Valid @RequestBody UpdateRiderLimitsRequest request) {
        riderService.updateLimits(id, request.maxConcurrentOrders(), request.pickupRadiusM());
        return ApiResponse.ok();
    }

    @RequirePermission("delivery:config:list")
    @GetMapping("/dispatch-config")
    public ApiResponse<DispatchConfigView> getConfig(
            @RequestParam(required = false) Long shopId) {
        return ApiResponse.ok(dispatchConfigService.getConfig(shopId));
    }

    @RequirePermission("delivery:config:update")
    @OperationLog(module = "骑手管理", action = "保存派单策略")
    @PutMapping("/dispatch-config")
    public ApiResponse<DispatchConfigView> saveConfig(
            @Valid @RequestBody SaveDispatchConfigRequest request) {
        return ApiResponse.ok(dispatchConfigService.save(new SaveDispatchConfigCommand(
                request.configScope(),
                request.shopId(),
                request.distanceWeight(),
                request.loadWeight(),
                request.ratingWeight(),
                request.timeoutWeight(),
                request.dispatchExpireSeconds(),
                request.maxRetry(),
                request.grabRadiusM(),
                request.grabMaxOrders(),
                request.grabEnabled(),
                request.autoDispatchEnabled(),
                request.status())));
    }
}
