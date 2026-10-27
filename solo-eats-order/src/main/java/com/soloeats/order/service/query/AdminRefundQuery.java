package com.soloeats.order.service.query;

import java.time.LocalDate;

/**
 * 平台售后列表查询条件。keyword 同时匹配退款单号与关联订单号。
 */
public record AdminRefundQuery(
        Integer status,
        String keyword,
        LocalDate dateFrom,
        LocalDate dateTo,
        long pageNum,
        long pageSize
) {
}
