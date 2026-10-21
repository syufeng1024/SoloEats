package com.soloeats.order.service;

import com.soloeats.order.service.result.CancelResult;
import com.soloeats.order.service.result.RefundResult;

public interface RefundService {

    /**
     * 用户发起全额退款。允许 WAIT_ACCEPT / PREPARING。
     */
    RefundResult refund(Long orderId, String reason, String idempotencyKey);

    /**
     * 商家拒单并全额退款。只允许 WAIT_ACCEPT。
     */
    RefundResult merchantReject(Long orderId, Long merchantId, String reason, String idempotencyKey);

    /**
     * 用户/商家主动取消：
     * - WAIT_PAY：释放库存和券，订单取消，无退款；
     * - WAIT_ACCEPT / PREPARING：走全额退款；
     * - 已取消：幂等返回最近退款信息。
     */
    CancelResult cancel(Long orderId, String reason, Integer operatorType, String idempotencyKey);
}
