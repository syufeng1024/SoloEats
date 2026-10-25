package com.soloeats.order.service.view;

/**
 * 商家工作台计数。一次条件聚合查询得出，不做营业额与趋势聚合。
 */
public record MerchantDashboardView(
        /** 今日订单数：created_at >= 今日 0 点。 */
        Long todayOrderCount,
        /** 待接单：status = 10。 */
        Long pendingAcceptCount,
        /** 备餐中：status = 20。 */
        Long preparingCount,
        /** 待用户取餐：自取单 status = 31。 */
        Long waitPickupCount,
        /** 今日完成：status = 60 且 completed_at >= 今日 0 点。 */
        Long completedTodayCount
) {
}
