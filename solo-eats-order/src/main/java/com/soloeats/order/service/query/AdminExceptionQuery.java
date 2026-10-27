package com.soloeats.order.service.query;

import java.time.LocalDate;

/**
 * 平台异常订单池查询条件。status 为订单状态，不是 t_order_exception.status。
 */
public record AdminExceptionQuery(
        Integer exceptionType,
        Integer status,
        Long merchantId,
        Long shopId,
        String keyword,
        LocalDate dateFrom,
        LocalDate dateTo,
        long pageNum,
        long pageSize
) {
}
