package com.soloeats.app.vo;

import java.math.BigDecimal;

/**
 * 骑手评分与履约统计，直接取自 t_rider。
 * rating 为 5 分制（decimal(3,2)）；onTimeRate 为百分比数值 0~100（decimal(5,2)），不是 0~1。
 */
public record RiderStatsView(
        BigDecimal rating,
        BigDecimal onTimeRate,
        Integer totalOrders,
        Integer certStatus
) {
}
