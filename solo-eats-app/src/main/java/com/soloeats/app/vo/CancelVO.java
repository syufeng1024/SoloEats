package com.soloeats.app.vo;

import com.soloeats.order.service.result.CancelResult;

public record CancelVO(
        Long orderId,
        Integer orderStatus,
        String refundNo,
        Long refundAmount
) {

    public static CancelVO from(CancelResult result) {
        return new CancelVO(
                result.orderId(),
                result.orderStatus(),
                result.refundNo(),
                result.refundAmount()
        );
    }
}
