package com.soloeats.product.service.view;

import java.util.List;

/**
 * 用户端门店菜单：分类 -> 商品 -> 规格 SKU -> 加料组，一次聚合返回。
 */
public record ShopMenuView(
        Long categoryId,
        String categoryName,
        List<MenuItemView> items
) {

    public record MenuItemView(
            Long spuId,
            String spuName,
            String subtitle,
            String mainImage,
            Long minPrice,
            Integer monthSales,
            List<String> tags,
            boolean soldOut,
            Long packagingFee,
            Integer minPurchaseQty,
            Integer limitPerOrder,
            List<MenuItemSkuView> skus,
            List<MenuAddonGroupView> addonGroups
    ) {
    }

    public record MenuItemSkuView(
            Long skuId,
            String skuName,
            String specJson,
            String imageUrl,
            Long price,
            Integer availableQty,
            boolean soldOut
    ) {
    }

    public record MenuAddonGroupView(
            Long groupId,
            String groupName,
            Integer minSelect,
            Integer maxSelect,
            List<MenuAddonItemView> items
    ) {
    }

    public record MenuAddonItemView(
            Long itemId,
            String itemName,
            Long price,
            Integer stock
    ) {
    }
}
