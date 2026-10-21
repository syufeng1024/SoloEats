package com.soloeats.order.service;

import java.time.LocalDateTime;

/**
 * 商家呼叫骑手后的派单状态。订单状态不变，只推进配送单派单窗口。
 */
public record RiderDispatchResult(
        Long orderId,
        Integer orderStatus,
        String orderStatusName,
        String deliveryNo,
        Integer deliveryStatus,
        Integer dispatchCount,
        LocalDateTime dispatchExpireAt
) {
}
