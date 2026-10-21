package com.soloeats.settlement.service.view;

import java.util.List;

public record SettlementListResult(
        List<SettlementBillView> bills,
        SettlementSummary summary
) {
}
