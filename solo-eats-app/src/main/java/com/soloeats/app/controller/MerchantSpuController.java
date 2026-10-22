package com.soloeats.app.controller;

import com.soloeats.app.dto.PutOnShopRequest;
import com.soloeats.app.dto.UpdateShopSpuRequest;
import com.soloeats.app.dto.UpdateShopSpuStatusRequest;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.product.service.ShopSpuService;
import com.soloeats.product.service.SpuService;
import com.soloeats.product.service.command.PutOnShopCommand;
import com.soloeats.product.service.command.UpdateShopSpuCommand;
import com.soloeats.product.service.view.ShopSpuView;
import com.soloeats.product.service.view.SpuView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家商品库与门店商品。merchantId 一律取自登录令牌。
 */
@RequireRole(Roles.MERCHANT)
@RestController
@RequiredArgsConstructor
public class MerchantSpuController {

    private final SpuService spuService;
    private final ShopSpuService shopSpuService;
    private final PermissionGuard permissionGuard;

    @GetMapping("/api/v1/merchant/spus")
    public ApiResponse<List<SpuView>> library(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(spuService.merchantLibrary(
                permissionGuard.requireMerchantId(), categoryId, keyword));
    }

    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "门店商品", action = "上架商品")
    @PostMapping("/api/v1/merchant/shop-spus")
    public ApiResponse<ShopSpuView> putOn(@Valid @RequestBody PutOnShopRequest request) {
        return ApiResponse.ok(shopSpuService.putOn(
                permissionGuard.requireMerchantId(),
                new PutOnShopCommand(
                        request.shopId(), request.spuId(),
                        request.priceOverride(), request.sortWeight())
        ));
    }

    @GetMapping("/api/v1/merchant/shop-spus")
    public ApiResponse<PageResult<ShopSpuView>> page(
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        Long resolvedShopId = permissionGuard.resolveOptionalShopId(shopId);
        if (resolvedShopId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "shopId 不能为空");
        }
        return ApiResponse.ok(shopSpuService.page(
                resolvedShopId, status, keyword, pageNum, pageSize));
    }

    @OperationLog(module = "门店商品", action = "修改门店商品")
    @PutMapping("/api/v1/merchant/shop-spus/{id}")
    public ApiResponse<ShopSpuView> update(@PathVariable Long id,
                                           @RequestBody UpdateShopSpuRequest request) {
        return ApiResponse.ok(shopSpuService.update(
                id,
                permissionGuard.requireMerchantId(),
                new UpdateShopSpuCommand(request.priceOverride(), request.sortWeight())
        ));
    }

    @OperationLog(module = "门店商品", action = "变更门店商品状态")
    @PutMapping("/api/v1/merchant/shop-spus/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody UpdateShopSpuStatusRequest request) {
        shopSpuService.updateStatus(id, permissionGuard.requireMerchantId(), request.status());
        return ApiResponse.ok();
    }
}
