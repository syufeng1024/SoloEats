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
}
