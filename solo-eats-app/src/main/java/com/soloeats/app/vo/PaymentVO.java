package com.soloeats.app.vo;

import com.soloeats.payment.service.PaymentResult;

public record PaymentVO(
        Long paymentId,
        String paymentNo,
        Long orderId,
        Integer channel,
        Long amount,
        Integer status
) {

    public static PaymentVO from(PaymentResult result) {
        return new PaymentVO(
                result.paymentId(),
                result.paymentNo(),
                result.orderId(),
                result.channel(),
                result.amount(),
                result.status()
        );
    }
}
