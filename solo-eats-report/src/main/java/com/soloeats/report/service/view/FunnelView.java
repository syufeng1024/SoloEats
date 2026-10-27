package com.soloeats.report.service.view;

import java.math.BigDecimal;

/**
 * 流量转化漏斗。五级量为区间汇总，四级转化率为百分比数值（10.00 表示 10.00%），
 * 分母为 0 时返回 0.00。
 */
public record FunnelView(
        long exposeCount,
        long clickCount,
        long cartCount,
        long orderCount,
        long payCount,
        BigDecimal clickRate,
        BigDecimal cartRate,
        BigDecimal orderRate,
        BigDecimal payRate
) {
}
