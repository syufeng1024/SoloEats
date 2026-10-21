package com.soloeats.common.payment;

public record PaymentInfo(
        Long paymentId,
        String paymentNo,
        Long amount,
        Integer status
) {
}
