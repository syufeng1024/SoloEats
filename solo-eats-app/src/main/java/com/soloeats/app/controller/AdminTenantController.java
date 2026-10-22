package com.soloeats.app.controller;

import com.soloeats.app.dto.SavePackageRequest;
import com.soloeats.app.dto.SaveTenantRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.system.service.SysTenantService;
import com.soloeats.system.service.command.SavePackageCommand;
import com.soloeats.system.service.command.SaveTenantCommand;
import com.soloeats.system.service.view.PackageView;
import com.soloeats.system.service.view.TenantView;
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

import java.util.List;

/**
 * 平台租户与套餐管理。只有平台租户自身（tenant 1）可操作，因此均要求 ADMIN 角色。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminTenantController {

    private final SysTenantService sysTenantService;

    @RequirePermission("system:tenant:list")
    @GetMapping("/tenants")
    public ApiResponse<PageResult<TenantView>> list(
            @RequestParam(required = false) Integer tenantType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(sysTenantService.page(tenantType, status, keyword, pageNum, pageSize));
    }

    @RequirePermission("system:tenant:create")
    @Idempotent(ttlSeconds = 60)
    @PostMapping("/tenants")
    public ApiResponse<TenantView> create(@Valid @RequestBody SaveTenantRequest request) {
        return ApiResponse.ok(sysTenantService.create(new SaveTenantCommand(
                request.tenantCode(),
                request.tenantName(),
                request.tenantType(),
                request.merchantId(),
                request.packageId(),
                request.expireAt(),
                request.contactName(),
                request.contactPhone())));
    }

    @RequirePermission("system:tenant:update")
    @PutMapping("/tenants/{id}")
    public ApiResponse<TenantView> update(
            @PathVariable Long id,
            @Valid @RequestBody SaveTenantRequest request) {
        return ApiResponse.ok(sysTenantService.update(id, new SaveTenantCommand(
                request.tenantCode(),
                request.tenantName(),
                request.tenantType(),
                request.merchantId(),
                request.packageId(),
                request.expireAt(),
                request.contactName(),
                request.contactPhone())));
    }

    @RequirePermission("system:tenant:update")
    @PutMapping("/tenants/{id}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody SaveTenantRequest.TenantStatusRequest request) {
        sysTenantService.updateStatus(id, request.status());
        return ApiResponse.ok();
    }

    @RequirePermission("system:package:list")
    @GetMapping("/packages")
    public ApiResponse<List<PackageView>> listPackages() {
        return ApiResponse.ok(sysTenantService.listPackages());
    }

    @RequirePermission("system:package:create")
    @Idempotent(ttlSeconds = 60)
    @PostMapping("/packages")
    public ApiResponse<PackageView> createPackage(@Valid @RequestBody SavePackageRequest request) {
        return ApiResponse.ok(sysTenantService.createPackage(toCommand(request)));
    }

    @RequirePermission("system:package:update")
    @PutMapping("/packages/{id}")
    public ApiResponse<PackageView> updatePackage(
            @PathVariable Long id,
            @Valid @RequestBody SavePackageRequest request) {
        return ApiResponse.ok(sysTenantService.updatePackage(id, toCommand(request)));
    }

    private SavePackageCommand toCommand(SavePackageRequest request) {
        return new SavePackageCommand(
                request.packageCode(),
                request.packageName(),
                request.menuIds(),
                request.maxShop(),
                request.maxUser(),
                request.price(),
                request.validDays(),
                request.status(),
                request.remark());
    }
}
