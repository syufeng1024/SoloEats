package com.soloeats.marketing.service;

public interface CouponService {

    /**
     * 校验并锁定优惠券。锁定时把 user_coupon.status 从 0 改成 1，并写入 order_id。
     */
    CouponLockResult lock(Long userId, Long userCouponId, Long orderId, Long goodsAmount);

    /**
     * 取消/超时释放优惠券。
     */
    void release(Long userCouponId, Long orderId);

    /**
     * 支付成功后核销优惠券。
     */
    void consume(Long userCouponId, Long orderId);

    /**
     * 退款回滚已核销优惠券：未过期恢复为未使用，已过期置为已退款。
     */
    void rollback(Long userCouponId, Long orderId);
}
