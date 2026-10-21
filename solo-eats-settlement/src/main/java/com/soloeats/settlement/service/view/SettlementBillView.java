package com.soloeats.settlement.service.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SettlementBillView(
        Long billId,
        String billNo,
        Long merchantId,
        Long shopId,
        LocalDate billDate,
        Long grossAmount,
        Long merchantDiscount,
        Long platformDiscount,
        Long commissionBase,
        Long platformCommission,
        Long merchantAmount,
        Long performanceFee,
        Long refundAmount,
        Integer status,
        LocalDateTime createdAt
) {
}
