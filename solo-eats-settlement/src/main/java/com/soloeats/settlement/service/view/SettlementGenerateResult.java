package com.soloeats.settlement.service.view;

import java.util.List;

public record SettlementGenerateResult(
        int billCount,
        int orderCount,
        List<Long> billIds
) {
}
