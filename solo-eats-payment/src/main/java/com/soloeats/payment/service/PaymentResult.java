package com.soloeats.payment.service;

public record PaymentResult(
        Long paymentId,
        String paymentNo,
        Long orderId,
        Integer channel,
        Long amount,
        Integer status
) {
}
