package com.soloeats.delivery.service.command;

import java.time.LocalDate;

/**
 * 后台配送单列表查询条件。dateFrom/dateTo 闭区间，按 created_at 过滤。
 */
public record DeliveryQuery(
        Integer status,
        Long shopId,
        Long riderId,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
