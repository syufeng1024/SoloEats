package com.soloeats.order.service.query;

import java.time.LocalDate;

public record AdminOrderQuery(
        Long merchantId,
        Long shopId,
        Integer status,
        String keyword,
        LocalDate dateFrom,
        LocalDate dateTo,
        long pageNum,
        long pageSize
) {
}
