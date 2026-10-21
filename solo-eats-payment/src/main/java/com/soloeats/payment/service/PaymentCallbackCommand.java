package com.soloeats.payment.service;

public record PaymentCallbackCommand(
        String paymentNo,
        String tradeNo,
        Long amount,
        String sign
) {
}
