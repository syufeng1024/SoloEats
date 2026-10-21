package com.soloeats.settlement.service.view;

import java.util.List;

public record SettlementDetailResult(
        SettlementBillView bill,
        List<SettlementOrderView> orders,
        List<SettlementItemView> items
) {
}
