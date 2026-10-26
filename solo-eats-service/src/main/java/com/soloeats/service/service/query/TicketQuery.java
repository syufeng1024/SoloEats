package com.soloeats.service.service.query;

import java.time.LocalDate;

/**
 * 后台工单列表筛选。日期按自然日 [dateFrom, dateTo] 解释，闭区间。
 */
public record TicketQuery(
        Integer status,
        Integer ticketType,
        Long assigneeId,
        Integer priority,
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
