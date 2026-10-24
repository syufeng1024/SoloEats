package com.soloeats.delivery.service;

import com.soloeats.delivery.domain.entity.DeliveryOrder;

import java.math.BigDecimal;

public interface DeliveryService {

    DeliveryOrder getByOrderId(Long orderId);

    /**
     * 支付成功后为平台配送订单创建配送单，状态 0 待派单。
     */
    void createForOrder(Long orderId, Long shopId, String pickupAddress,
                        String receiverAddress, BigDecimal receiverLng,
                        BigDecimal receiverLat, Long deliveryFee);

    /**
     * 商家呼叫骑手：配送单 0/10/71 -> 10，写入派单次数与派单过期时间。
     * 不绑定骑手，骑手仍需调用 accept 接单。
     */
    DeliveryOrder dispatch(Long orderId);

    /**
     * 骑手接单：配送单 0/10 -> 20。
     */
    DeliveryOrder accept(Long orderId, Long riderId);

    /**
     * 骑手取货：配送单 20 -> 40。
     */
    DeliveryOrder pickup(Long orderId, Long riderId);

    /**
     * 骑手送达：配送单 40 -> 50，并写入骑手收入明细（结算前置）。
     */
    DeliveryOrder deliver(Long orderId, Long riderId);

    /**
     * 骑手拒单：dispatch_count + 1。
     * releaseToHall=true 时清空骑手、派单过期时间并把配送单退回待派单（10 -> 0）；
     * 仅当配送单仍处于已派单（10）且 rider_id 为空或等于该骑手时生效。
     */
    DeliveryOrder reject(Long orderId, Long riderId, boolean releaseToHall);

    /**
     * 改派释放：仅当配送单仍由该骑手持有且处于 10/20/30/40 时生效，
     * 清空 rider_id、退回 0 待派单并重置派单次数与倒计时。不改 order_id / delivery_no。
     */
    DeliveryOrder releaseForReassign(Long orderId, Long riderId);
}
