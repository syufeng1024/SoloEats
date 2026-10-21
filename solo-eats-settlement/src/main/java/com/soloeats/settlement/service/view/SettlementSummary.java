package com.soloeats.settlement.service.view;

public record SettlementSummary(
        long billCount,
        long grossAmount,
        long merchantAmount,
        long platformCommission,
        long payableAmount
) {
}
