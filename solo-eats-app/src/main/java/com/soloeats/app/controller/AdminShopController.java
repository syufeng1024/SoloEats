package com.soloeats.app.controller;

import com.soloeats.app.dto.SaveDeliveryAreaRequest;
import com.soloeats.app.dto.SaveShopRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.auth.RequirePermission;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.store.service.ShopGeoService;
import com.soloeats.store.service.ShopService;
import com.soloeats.store.service.command.SaveDeliveryAreaCommand;
import com.soloeats.store.service.command.SaveShopCommand;
import com.soloeats.store.service.view.ShopDetailView;
import com.soloeats.store.service.view.ShopView;
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
 * 平台侧门店管理。门店列表按当前平台角色的 data_scope 过滤（本租户/本商家/本门店）。
 */
@RequireRole(Roles.ADMIN)
@RestController
@RequestMapping("/api/v1/admin/shops")
@RequiredArgsConstructor
public class AdminShopController {

    private final ShopService shopService;
    private final ShopGeoService shopGeoService;

    @RequirePermission("store:shop:list")
    @GetMapping
    public ApiResponse<PageResult<ShopView>> list(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(shopService.adminPage(
                merchantId, status, city, keyword, pageNum, pageSize));
    }

    @RequirePermission("store:shop:create")
    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "门店管理", action = "新增门店")
    @PostMapping
    public ApiResponse<ShopDetailView> create(@Valid @RequestBody SaveShopRequest request) {
        return ApiResponse.ok(shopService.createForAdmin(toCommand(request)));
    }

    @RequirePermission("store:shop:update")
    @OperationLog(module = "门店管理", action = "编辑门店")
    @PutMapping("/{id}")
    public ApiResponse<ShopDetailView> update(@PathVariable Long id,
                                              @Valid @RequestBody SaveShopRequest request) {
        return ApiResponse.ok(shopService.update(id, toCommand(request), null));
    }

    @RequirePermission("store:shop:update")
    @OperationLog(module = "门店管理", action = "变更门店状态")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody SaveShopRequest.StatusRequest request) {
        shopService.updateStatus(id, request.status(), null);
        return ApiResponse.ok();
    }

    @RequirePermission("store:shop:update")
    @OperationLog(module = "门店管理", action = "配置配送围栏")
    @PutMapping("/{id}/delivery-area")
    public ApiResponse<ShopGeoService.DeliveryAreaView> saveDeliveryArea(
            @PathVariable Long id,
            @Valid @RequestBody SaveDeliveryAreaRequest request) {
        return ApiResponse.ok(shopGeoService.saveDeliveryArea(id, toCommand(request)));
    }

    @RequirePermission("store:shop:list")
    @GetMapping("/{id}/delivery-area")
    public ApiResponse<ShopGeoService.DeliveryAreaView> getDeliveryArea(@PathVariable Long id) {
        return ApiResponse.ok(shopGeoService.getDeliveryArea(id));
    }

    private SaveShopCommand toCommand(SaveShopRequest request) {
        return new SaveShopCommand(
                request.merchantId(),
                request.shopName(),
                request.brandId(),
                request.categoryId(),
                request.province(),
                request.city(),
                request.district(),
                request.address(),
                request.lng(),
                request.lat(),
                request.contactName(),
                request.contactPhone(),
                request.logoUrl(),
                request.coverUrl(),
                request.imageUrls(),
                request.announcement(),
                request.deliveryTypes(),
                request.minOrderAmount(),
                request.deliveryFee(),
                request.packagingFee(),
                request.prepMinutes(),
                request.deliveryRadiusM(),
                request.sortWeight(),
                request.settleAccountId());
    }

    private SaveDeliveryAreaCommand toCommand(SaveDeliveryAreaRequest request) {
        List<SaveDeliveryAreaCommand.Point> polygon = request.polygon() == null
                ? null
                : request.polygon().stream()
                .map(point -> new SaveDeliveryAreaCommand.Point(point.lng(), point.lat()))
                .toList();
        return new SaveDeliveryAreaCommand(
                request.areaType(),
                request.centerLng(),
                request.centerLat(),
                request.radiusM(),
                polygon);
    }
}
