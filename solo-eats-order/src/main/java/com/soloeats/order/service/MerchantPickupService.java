package com.soloeats.order.service;

import com.soloeats.order.domain.enums.OrderStatus;

/**
 * 商家自取核销。状态流转统一交给 OrderApplicationService#transit，禁止直接 UPDATE 订单状态。
 */
public interface MerchantPickupService {

    /**
     * 核销自取订单的取餐码，WAIT_USER_PICKUP(31) --PICKUP_VERIFY--> PICKED_UP(51)。
     *
     * <p>订单归属由 app 层 PermissionGuard#assertMerchantOrder 校验，本方法再做一次商家一致性兜底。
     *
     * @param orderId    订单 id
     * @param merchantId 当前登录商家的业务 id（t_merchant.id）
     * @param pickupCode 用户出示的取餐码
     * @return 流转后的订单状态
     */
    OrderStatus verifyPickup(Long orderId, Long merchantId, String pickupCode);
}
