package com.soloeats.report.service.view;

/**
 * 平台工作台今日实时卡。金额单位分。
 *
 * @param todayGmv        今日已完成订单 GMV（剔除全额退款）
 * @param orderCount      今日已完成订单数
 * @param avgOrderAmount  客单价，orderCount = 0 时为 0
 * @param onlineRiders    当前在线骑手数
 * @param exceptionOrders 今日异常订单数（未取消且 exception_type &lt;&gt; 0）
 * @param newUsers        今日新增用户数
 * @param inProgressOrders 当前进行中订单数
 */
public record DashboardView(
        long todayGmv,
        long orderCount,
        long avgOrderAmount,
        long onlineRiders,
        long exceptionOrders,
        long newUsers,
        long inProgressOrders
) {
}
