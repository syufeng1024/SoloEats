package com.soloeats.product.mapper;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门店商品列表联表投影，避免逐行回查 SPU 造成 N+1。
 */
@Data
public class ShopSpuRow {

    private Long id;

    private Long shopId;

    private Long spuId;

    private Long priceOverride;

    private Integer status;

    private Integer auditStatus;

    private Integer sortWeight;

    private Integer monthSales;

    private LocalDateTime suspendedUntil;

    private String remark;

    private LocalDateTime updatedAt;

    private String spuName;

    private String mainImage;

    private Long skuMinPrice;
}
