package com.soloeats.payment.service.event;

public record PaymentSuccessEvent(
        Long orderId,
        String paymentNo,
        String tradeNo,
        Long amount
) {
}
