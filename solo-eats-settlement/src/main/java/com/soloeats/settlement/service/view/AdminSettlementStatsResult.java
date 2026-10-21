package com.soloeats.settlement.service.view;

import java.util.List;

public record AdminSettlementStatsResult(
        List<SettlementBillView> bills,
        AdminSettlementSummary summary,
        long total,
        long pageNum,
        long pageSize
) {
}
