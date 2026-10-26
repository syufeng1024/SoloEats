package com.soloeats.service.service.view;

import java.util.List;

/**
 * 工单详情：工单 + 流转记录 + 关联投诉（无投诉时为 null）。
 */
public record TicketDetailView(
        TicketView ticket,
        List<TicketRecordView> records,
        ComplaintView complaint
) {
}
