package com.soloeats.app.controller;

import com.soloeats.app.dto.DeliveryCheckRequest;
import com.soloeats.common.api.ApiResponse;
import com.soloeats.common.api.PageResult;
import com.soloeats.store.service.ShopGeoService;
import com.soloeats.store.service.ShopService;
import com.soloeats.store.service.command.DeliveryCheckCommand;
import com.soloeats.store.service.view.DeliveryCheckResult;
import com.soloeats.store.service.view.NearbyShopView;
import com.soloeats.store.service.view.ShopDetailView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 用户端门店浏览与下单前校验。白名单接口，不要求后台角色。
 */
@RestController
@RequestMapping("/api/v1/c/shops")
@RequiredArgsConstructor
public class ShopClientController {

    private final ShopService shopService;
    private final ShopGeoService shopGeoService;

    @GetMapping("/{id}")
    public ApiResponse<ShopDetailView> detail(@PathVariable Long id) {
        return ApiResponse.ok(shopService.detail(id));
    }

    @GetMapping
    public ApiResponse<PageResult<NearbyShopView>> nearby(
            @RequestParam BigDecimal lng,
            @RequestParam BigDecimal lat,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "comprehensive") String sortBy,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(shopGeoService.nearby(
                lng, lat, radius, keyword, sortBy, pageNum, pageSize));
    }

    @PostMapping("/{id}/delivery-check")
    public ApiResponse<DeliveryCheckResult> deliveryCheck(@PathVariable Long id,
                                                          @Valid @RequestBody DeliveryCheckRequest request) {
        return ApiResponse.ok(shopGeoService.check(id, new DeliveryCheckCommand(
                request.lng(), request.lat(), request.goodsAmount(), request.fulfillmentType())));
    }

    @GetMapping("/{id}/business-status")
    public ApiResponse<ShopService.BusinessStatus> businessStatus(@PathVariable Long id) {
        return ApiResponse.ok(shopService.businessStatus(id));
    }
}
