package com.soloeats.app.vo;

import com.soloeats.order.service.result.RefundResult;

public record RefundVO(
        Long refundId,
        String refundNo,
        Long orderId,
        Long amount,
        Integer refundStatus,
        Integer orderStatus,
        String channelRefundNo
) {

    public static RefundVO from(RefundResult result) {
        return new RefundVO(
                result.refundId(),
                result.refundNo(),
                result.orderId(),
                result.amount(),
                result.refundStatus(),
                result.orderStatus(),
                result.channelRefundNo()
        );
    }
}
