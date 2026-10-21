package com.soloeats.app.controller;

import com.soloeats.app.dto.CreateSkuRequest;
import com.soloeats.app.dto.UpdateSkuRequest;
import com.soloeats.app.dto.UpdateSkuStockRequest;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.product.service.MerchantSkuService;
import com.soloeats.product.service.command.CreateSkuCommand;
import com.soloeats.product.service.command.UpdateSkuCommand;
import com.soloeats.product.service.command.UpdateSkuStockCommand;
import com.soloeats.product.service.view.MerchantSkuView;
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

@RequireRole(Roles.MERCHANT)
@RestController
@RequestMapping("/api/v1/merchant/skus")
@RequiredArgsConstructor
public class MerchantSkuController {

    private final MerchantSkuService merchantSkuService;
    private final PermissionGuard permissionGuard;

    @GetMapping
    public ApiResponse<PageResult<MerchantSkuView>> list(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(merchantSkuService.page(
                permissionGuard.resolveShopId(shopId),
                status, keyword, pageNum, pageSize
        ));
    }

    @PostMapping
    public ApiResponse<MerchantSkuView> create(@Valid @RequestBody CreateSkuRequest request) {
        Long shopId = permissionGuard.resolveShopId(null);
        CreateSkuCommand command = new CreateSkuCommand(
                request.spuId(),
                request.skuName(),
                request.specJson(),
                request.imageUrl(),
                request.price(),
                request.status(),
                request.initialStock()
        );
        return ApiResponse.ok(merchantSkuService.create(shopId, command));
    }

    @PutMapping("/{skuId}")
    public ApiResponse<MerchantSkuView> update(
            @PathVariable Long skuId,
            @Valid @RequestBody UpdateSkuRequest request) {
        Long shopId = permissionGuard.resolveShopId(null);
        UpdateSkuCommand command = new UpdateSkuCommand(
                request.skuName(),
                request.specJson(),
                request.imageUrl(),
                request.price(),
                request.status()
        );
        return ApiResponse.ok(merchantSkuService.update(shopId, skuId, command));
    }

    @PutMapping("/{skuId}/stock")
    public ApiResponse<MerchantSkuView> updateStock(
            @PathVariable Long skuId,
            @Valid @RequestBody UpdateSkuStockRequest request) {
        Long shopId = permissionGuard.resolveShopId(null);
        UpdateSkuStockCommand command = new UpdateSkuStockCommand(
                request.availableQty(),
                request.soldOut()
        );
        return ApiResponse.ok(merchantSkuService.updateStock(shopId, skuId, command));
    }
}
