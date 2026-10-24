package com.soloeats.delivery.service.view;

import java.math.BigDecimal;

/**
 * 骑手收入概览。金额单位分；onTimeRate 取 t_rider.on_time_rate，未设置时为 0。
 */
public record RiderIncomeOverview(
        long todayAmount,
        long todayOrders,
        long weekAmount,
        long monthAmount,
        BigDecimal onTimeRate
) {
}
