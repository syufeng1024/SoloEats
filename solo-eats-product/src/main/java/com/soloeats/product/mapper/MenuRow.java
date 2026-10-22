package com.soloeats.product.mapper;

import lombok.Data;

/**
 * 用户端菜单一次聚合查询的扁平行：门店商品 -> SPU -> SKU -> 库存。
 * 由 ShopMenuService 在内存中按 category/spu 分组，不做逐行回查。
 */
@Data
public class MenuRow {

    private Long categoryId;

    private String categoryName;

    private Integer categorySort;

    private Long shopSpuId;

    private Long spuId;

    private Long priceOverride;

    private Integer shopSpuStatus;

    private Integer spuSort;

    private String spuName;

    private String subtitle;

    private String mainImage;

    private String tagsJson;

    private Long packagingFee;

    private Integer minPurchaseQty;

    private Integer limitPerOrder;

    private Integer monthSales;

    private Long skuId;

    private String skuName;

    private String specJson;

    private String skuImage;

    private Long price;

    private Integer availableQty;

    private Boolean soldOut;

    private Integer skuSort;
}
