package com.soloeats.app.controller;

import com.soloeats.app.dto.CreateShopAccountRequest;
import com.soloeats.app.dto.SaveBusinessHourRequest;
import com.soloeats.app.dto.SaveShopRequest;
import com.soloeats.app.security.PermissionGuard;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.auth.AuthContext;
import com.soloeats.common.auth.RequireRole;
import com.soloeats.common.auth.Roles;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.idempotency.Idempotent;
import com.soloeats.common.log.OperationLog;
import com.soloeats.store.service.ShopAccountService;
import com.soloeats.store.service.ShopDecorationService;
import com.soloeats.store.service.ShopService;
import com.soloeats.store.service.command.CreateShopAccountCommand;
import com.soloeats.store.service.command.SaveBusinessHourCommand;
import com.soloeats.store.service.command.SaveShopCommand;
import com.soloeats.store.service.view.ShopAccountView;
import com.soloeats.store.service.view.ShopDetailView;
import com.soloeats.store.service.view.ShopView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商家中心门店管理。merchantId 一律取登录令牌；
 * 门店账号（t_sys_user.shop_id 非空）只能看到并操作自己门店。
 */
@RequireRole({Roles.MERCHANT, "MERCHANT_MANAGER", "MERCHANT_CLERK"})
@RestController
@RequestMapping("/api/v1/merchant/shops")
@RequiredArgsConstructor
public class MerchantShopController {

    private final ShopService shopService;
    private final ShopAccountService shopAccountService;
    private final ShopDecorationService shopDecorationService;
    private final PermissionGuard permissionGuard;

    @GetMapping
    public ApiResponse<List<ShopView>> list() {
        return ApiResponse.ok(shopService.listByMerchant(
                permissionGuard.requireMerchantId(), operatorShopId()));
    }

    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "门店管理", action = "商家建店")
    @PostMapping
    public ApiResponse<ShopDetailView> create(@Valid @RequestBody SaveShopRequest request) {
        // 门店账号只负责本店经营，不允许再开新店
        if (operatorShopId() != null) {
            throw new BizException(ErrorCode.AUTH_FORBIDDEN, "门店账号不能新建门店");
        }
        Long merchantId = permissionGuard.requireMerchantId();
        return ApiResponse.ok(shopService.createForMerchant(merchantId, toCommand(request)));
    }

    @OperationLog(module = "门店管理", action = "编辑门店")
    @PutMapping("/{id}")
    public ApiResponse<ShopDetailView> update(@PathVariable Long id,
                                              @Valid @RequestBody SaveShopRequest request) {
        Long merchantId = permissionGuard.requireMerchantId();
        shopService.assertMerchantShop(id, merchantId, operatorShopId());
        return ApiResponse.ok(shopService.update(id, toCommand(request), merchantId));
    }

    @OperationLog(module = "门店管理", action = "替换营业时段")
    @PutMapping("/{id}/business-hours")
    public ApiResponse<Void> saveBusinessHours(@PathVariable Long id,
                                               @Valid @RequestBody SaveBusinessHourRequest request) {
        Long merchantId = permissionGuard.requireMerchantId();
        shopService.assertMerchantShop(id, merchantId, operatorShopId());
        List<SaveBusinessHourCommand> hours = request.hours().stream()
                .map(hour -> new SaveBusinessHourCommand(
                        hour.dayOfWeek(), hour.startTime(), hour.endTime()))
                .toList();
        shopService.saveBusinessHours(id, hours, merchantId);
        return ApiResponse.ok();
    }

    @OperationLog(module = "门店管理", action = "切换营业状态")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id,
                                          @Valid @RequestBody SaveShopRequest.StatusRequest request) {
        Long merchantId = permissionGuard.requireMerchantId();
        shopService.assertMerchantShop(id, merchantId, operatorShopId());
        shopService.updateStatus(id, request.status(), merchantId);
        return ApiResponse.ok();
    }

    @OperationLog(module = "门店管理", action = "配置到店自取")
    @PutMapping("/{id}/pickup")
    public ApiResponse<Void> updatePickup(@PathVariable Long id,
                                          @Valid @RequestBody SaveShopRequest.PickupRequest request) {
        Long merchantId = permissionGuard.requireMerchantId();
        shopService.assertMerchantShop(id, merchantId, operatorShopId());
        shopService.updatePickup(id, new SaveShopCommand.Pickup(
                request.pickupEnabled(),
                request.pickupPrepareMinutes(),
                request.pickupTimeoutMinutes(),
                request.pickupCodePrefix(),
                request.pickupCodeLength(),
                request.pickupCodeValidMinutes(),
                request.pickupDiscount()), merchantId);
        return ApiResponse.ok();
    }

    @Idempotent(ttlSeconds = 5)
    @OperationLog(module = "门店管理", action = "新增门店账号", recordParams = false)
    @PostMapping("/{id}/accounts")
    public ApiResponse<ShopAccountView> createAccount(
            @PathVariable Long id,
            @Valid @RequestBody CreateShopAccountRequest request) {
        Long merchantId = permissionGuard.requireMerchantId();
        shopService.assertMerchantShop(id, merchantId, operatorShopId());
        return ApiResponse.ok(shopAccountService.create(id, merchantId,
                new CreateShopAccountCommand(
                        request.username(),
                        request.password(),
                        request.nickname(),
                        request.phone(),
                        request.accountRole())));
    }

    @GetMapping("/{id}/accounts")
    public ApiResponse<List<ShopAccountView>> listAccounts(@PathVariable Long id) {
        Long merchantId = permissionGuard.requireMerchantId();
        shopService.assertMerchantShop(id, merchantId, operatorShopId());
        return ApiResponse.ok(shopAccountService.list(id, merchantId));
    }

    @OperationLog(module = "门店管理", action = "解绑门店账号")
    @DeleteMapping("/{id}/accounts/{userId}")
    public ApiResponse<Void> unbindAccount(@PathVariable Long id, @PathVariable Long userId) {
        Long merchantId = permissionGuard.requireMerchantId();
        shopService.assertMerchantShop(id, merchantId, operatorShopId());
        shopAccountService.unbind(id, merchantId, userId);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/decoration")
    public ApiResponse<String> getDecoration(@PathVariable Long id) {
        Long merchantId = permissionGuard.requireMerchantId();
        shopService.assertMerchantShop(id, merchantId, operatorShopId());
        return ApiResponse.ok(shopDecorationService.get(id, merchantId));
    }

    @OperationLog(module = "门店管理", action = "保存门店装修")
    @PutMapping("/{id}/decoration")
    public ApiResponse<String> saveDecoration(@PathVariable Long id,
                                              @Valid @RequestBody SaveShopRequest.DecorationRequest request) {
        Long merchantId = permissionGuard.requireMerchantId();
        shopService.assertMerchantShop(id, merchantId, operatorShopId());
        return ApiResponse.ok(shopDecorationService.save(id, merchantId, request.config().toString()));
    }

    /**
     * 商家管理员/模拟商家令牌可管理本商家全部门店；
     * 只有门店店员账号才被锁定到令牌里的 shopId。
     */
    private Long operatorShopId() {
        var principal = AuthContext.require();
        boolean manager = principal.hasRole(Roles.MERCHANT)
                || principal.hasRole("MERCHANT_MANAGER");
        return manager ? null : principal.shopId();
    }

    private SaveShopCommand toCommand(SaveShopRequest request) {
        return new SaveShopCommand(
                // 商家侧忽略请求里的 merchantId，统一取令牌
                null,
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
}
