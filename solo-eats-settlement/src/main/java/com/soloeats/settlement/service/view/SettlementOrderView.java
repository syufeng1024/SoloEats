package com.soloeats.settlement.service.view;

import java.time.LocalDateTime;

public record SettlementOrderView(
        Long orderId,
        String orderNo,
        Long payAmount,
        Long goodsAmount,
        Long packagingFee,
        Long deliveryFee,
        Long merchantDiscount,
        Long platformDiscount,
        Long commissionAmount,
        Long merchantAmount,
        LocalDateTime completedAt,
        LocalDateTime settleEligibleAt
) {
}
