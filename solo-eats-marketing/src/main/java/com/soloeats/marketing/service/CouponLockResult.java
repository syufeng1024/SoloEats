package com.soloeats.marketing.service;

/**
 * 优惠券锁定结果，供下单服务计算订单金额与分摊。
 */
public record CouponLockResult(
        Long userCouponId,
        Long templateId,
        Long discountAmount,
        Integer bearer
) {
}
