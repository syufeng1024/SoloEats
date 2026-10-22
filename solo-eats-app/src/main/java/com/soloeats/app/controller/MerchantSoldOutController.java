package com.soloeats.app.controller;

import com.soloeats.app.dto.RestoreSoldOutRequest;
import com.soloeats.app.dto.SoldOutRequest;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.product.service.SoldOutService;
import com.soloeats.product.service.command.SoldOutCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家批量估清/恢复。估清联动 t_sku_stock.sold_out、t_shop_spu.status、t_sold_out_record。
 */
@RequireRole(Roles.MERCHANT)
@RestController
@RequestMapping("/api/v1/merchant/sold-out")
@RequiredArgsConstructor
public class MerchantSoldOutController {

    private final SoldOutService soldOutService;
    private final PermissionGuard permissionGuard;

    @Idempotent(ttlSeconds = 10)
    @OperationLog(module = "估清管理", action = "批量估清")
    @PostMapping
    public ApiResponse<Void> markSoldOut(@Valid @RequestBody SoldOutRequest request) {
        UserPrincipal principal = permissionGuard.principal();
        soldOutService.markSoldOut(
                permissionGuard.requireMerchantId(),
                new SoldOutCommand(request.shopId(), request.skuIds(), request.reason()),
                principal.userId(), principal.username());
        return ApiResponse.ok();
    }

    @OperationLog(module = "估清管理", action = "批量恢复")
    @DeleteMapping
    public ApiResponse<Void> restore(@Valid @RequestBody RestoreSoldOutRequest request) {
        UserPrincipal principal = permissionGuard.principal();
        soldOutService.restore(
                permissionGuard.requireMerchantId(),
                new SoldOutCommand(request.shopId(), request.skuIds(), null),
                principal.userId(), principal.username());
        return ApiResponse.ok();
    }
}
