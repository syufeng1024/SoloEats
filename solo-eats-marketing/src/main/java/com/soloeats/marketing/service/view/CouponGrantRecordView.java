package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.UserCoupon;

import java.time.LocalDateTime;

/**
 * 定向发券后的用户券记录。status 语义沿用 t_user_coupon：
 * 0未用 1锁定 2已用 3过期 4作废 5已退款。
 */
public record CouponGrantRecordView(
        Long id,
        Long userId,
        Long templateId,
        Integer status,
        LocalDateTime expireAt,
        LocalDateTime usedAt,
        Long orderId,
        LocalDateTime createdAt
) {

    public static CouponGrantRecordView from(UserCoupon coupon) {
        return new CouponGrantRecordView(
                coupon.getId(),
                coupon.getUserId(),
                coupon.getTemplateId(),
                coupon.getStatus(),
                coupon.getExpireAt(),
                coupon.getUsedAt(),
                coupon.getOrderId(),
                coupon.getCreatedAt());
    }
}
