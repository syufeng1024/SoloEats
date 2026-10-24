package com.soloeats.delivery.service.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单笔骑手收入构成，金额单位分。
 */
public record RiderIncomeDetailView(
        Long id,
        Long deliveryOrderId,
        Long orderId,
        Long baseFee,
        Long distanceFee,
        Long timeSubsidy,
        Long weatherSubsidy,
        Long tipAmount,
        Long penaltyAmount,
        Long compensationAmount,
        Long totalAmount,
        LocalDate settlementDate,
        Integer status,
        LocalDateTime createdAt
) {
}
