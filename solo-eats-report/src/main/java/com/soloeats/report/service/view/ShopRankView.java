package com.soloeats.report.service.view;

import java.math.BigDecimal;

/**
 * 门店排行行。cancelRate 为百分比数值（3.25 表示 3.25%）。
 */
public record ShopRankView(
        Long shopId,
        String shopName,
        Long merchantId,
        long orderCount,
        long gmv,
        BigDecimal rating,
        int avgPrepMinutes,
        BigDecimal cancelRate
) {
}
