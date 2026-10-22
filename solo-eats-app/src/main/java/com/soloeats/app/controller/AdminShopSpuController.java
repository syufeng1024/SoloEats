package com.soloeats.app.controller;

import com.soloeats.app.dto.AuditShopSpuRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.log.OperationLog;
import com.soloeats.product.service.ShopSpuService;
import com.soloeats.product.service.view.ShopSpuView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台侧门店商品审核。P0 默认免审（audit_status=2），
 * app.product.price-audit-enabled=true 时改价/上架写 audit_status=1 进入本列表。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/shop-spus")
@RequiredArgsConstructor
public class AdminShopSpuController {

    private final ShopSpuService shopSpuService;

    @RequirePermission("product:shopspu:audit")
    @GetMapping
    public ApiResponse<PageResult<ShopSpuView>> list(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(shopSpuService.adminPage(
                shopId, auditStatus, keyword, pageNum, pageSize));
    }

    @RequirePermission("product:shopspu:audit")
    @OperationLog(module = "门店商品", action = "审核门店商品")
    @PutMapping("/{id}/audit")
    public ApiResponse<ShopSpuView> audit(@PathVariable Long id,
                                          @Valid @RequestBody AuditShopSpuRequest request) {
        return ApiResponse.ok(shopSpuService.audit(id, request.auditStatus(), request.remark()));
    }
}
