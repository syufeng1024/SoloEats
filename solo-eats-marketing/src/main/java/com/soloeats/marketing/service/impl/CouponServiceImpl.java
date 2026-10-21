package com.soloeats.marketing.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.CouponTemplate;
import com.soloeats.marketing.domain.entity.UserCoupon;
import com.soloeats.marketing.mapper.CouponTemplateMapper;
import com.soloeats.marketing.mapper.UserCouponMapper;
import com.soloeats.marketing.service.CouponLockResult;
import com.soloeats.marketing.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final UserCouponMapper userCouponMapper;
    private final CouponTemplateMapper couponTemplateMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponLockResult lock(Long userId, Long userCouponId, Long orderId, Long goodsAmount) {
        UserCoupon coupon = userCouponMapper.selectById(userCouponId);
        if (coupon == null || !Objects.equals(coupon.getUserId(), userId)) {
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE);
        }
        if (coupon.getStatus() == null || coupon.getStatus() != 0) {
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券已使用或已锁定");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getExpireAt() == null || !coupon.getExpireAt().isAfter(now)) {
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券已过期");
        }

        CouponTemplate template = couponTemplateMapper.selectById(coupon.getTemplateId());
        if (template == null || template.getStatus() == null || template.getStatus() != 0) {
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券模板已停用");
        }
        if (template.getValidStart() != null && now.isBefore(template.getValidStart())) {
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券未到使用时间");
        }
        if (template.getValidEnd() != null && now.isAfter(template.getValidEnd())) {
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "优惠券已过使用时间");
        }

        long threshold = template.getThresholdAmount() == null ? 0L : template.getThresholdAmount();
        if (goodsAmount == null || goodsAmount < threshold) {
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "未达到优惠券使用门槛");
        }

        int rows = userCouponMapper.lock(userCouponId, userId, orderId);
        if (rows != 1) {
            throw new BizException(ErrorCode.COUPON_LOCK_FAILED);
        }

        long discount = calculateDiscount(template, goodsAmount);
        int bearer = template.getBearer() == null ? 1 : template.getBearer();
        return new CouponLockResult(coupon.getId(), template.getId(), discount, bearer);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(Long userCouponId, Long orderId) {
        if (userCouponMapper.release(userCouponId, orderId) != 1) {
            throw new BizException(ErrorCode.COUPON_STATE_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(Long userCouponId, Long orderId) {
        if (userCouponMapper.consume(userCouponId, orderId) != 1) {
            throw new BizException(ErrorCode.COUPON_STATE_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long userCouponId, Long orderId) {
        if (userCouponMapper.rollbackValid(userCouponId, orderId) == 0) {
            userCouponMapper.rollbackExpired(userCouponId, orderId);
        }
    }

    private long calculateDiscount(CouponTemplate template, long goodsAmount) {
        long discount;
        if (Objects.equals(template.getCouponType(), 2)) {
            BigDecimal rate = template.getDiscountRate();
            if (rate == null) {
                throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "折扣券未配置折扣率");
            }
            long payable = BigDecimal.valueOf(goodsAmount)
                    .multiply(rate)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValue();
            discount = goodsAmount - payable;
        } else {
            discount = template.getDiscountAmount() == null ? 0L : template.getDiscountAmount();
        }
        discount = Math.min(discount, goodsAmount);
        if (discount <= 0) {
            throw new BizException(ErrorCode.COUPON_NOT_AVAILABLE, "优惠金额必须大于 0");
        }
        return discount;
    }
}
