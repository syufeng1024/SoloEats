package com.soloeats.order.service.query;

import java.time.LocalDate;

public record AdminOrderQuery(
        Long merchantId,
        Long shopId,
        Integer status,
        /** 按订单级退款标识筛选：0 无退款 / 1 部分退款 / 2 全额退款；为空表示不限。 */
        Integer refundStatus,
        String keyword,
        LocalDate dateFrom,
        LocalDate dateTo,
        long pageNum,
        long pageSize
) {
}
