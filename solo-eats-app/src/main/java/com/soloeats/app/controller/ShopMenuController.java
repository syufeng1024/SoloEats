package com.soloeats.app.controller;

import com.soloeats.common.api.ApiResponse;
import com.soloeats.product.service.ShopMenuService;
import com.soloeats.product.service.SpuService;
import com.soloeats.product.service.view.ShopMenuView;
import com.soloeats.product.service.view.SpuDetailView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端菜单与商品详情。白名单接口。
 */
@RestController
@RequestMapping("/api/v1/c")
@RequiredArgsConstructor
public class ShopMenuController {

    private final ShopMenuService shopMenuService;
    private final SpuService spuService;

    /**
     * 一次返回：分类 -> 商品 -> 规格 SKU -> 加料组，含估清状态与起售量。
     */
    @GetMapping("/shops/{shopId}/menu")
    public ApiResponse<List<ShopMenuView>> menu(@PathVariable Long shopId) {
        return ApiResponse.ok(shopMenuService.menu(shopId));
    }

    @GetMapping("/spus/{spuId}")
    public ApiResponse<SpuDetailView> detail(@PathVariable Long spuId) {
        return ApiResponse.ok(spuService.detail(spuId));
    }
}
