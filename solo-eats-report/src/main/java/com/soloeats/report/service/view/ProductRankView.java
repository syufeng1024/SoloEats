package com.soloeats.report.service.view;

/**
 * 商品榜行。saleAmount 为净销售额（应付减已退，分），refundQty 为已退数量。
 */
public record ProductRankView(
        Long spuId,
        Long skuId,
        String spuName,
        Long categoryId,
        long saleQty,
        long saleAmount,
        long refundQty
) {
}
