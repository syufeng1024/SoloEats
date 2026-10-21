package com.soloeats.settlement.service.view;

public record SettlementItemView(
        Long itemId,
        Long orderId,
        Integer itemType,
        Long amount,
        String remark
) {
}
