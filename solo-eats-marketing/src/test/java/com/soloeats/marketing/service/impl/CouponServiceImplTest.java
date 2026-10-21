package com.soloeats.marketing.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.CouponTemplate;
import com.soloeats.marketing.domain.entity.UserCoupon;
import com.soloeats.marketing.mapper.CouponTemplateMapper;
import com.soloeats.marketing.mapper.UserCouponMapper;
import com.soloeats.marketing.service.CouponLockResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    private static final long USER_ID = 1L;
    private static final long COUPON_ID = 3001L;
    private static final long ORDER_ID = 90001L;

    @Mock
    private UserCouponMapper userCouponMapper;

    @Mock
    private CouponTemplateMapper couponTemplateMapper;

    @InjectMocks
    private CouponServiceImpl couponService;

    @Test
    @DisplayName("满减券：达到门槛后按固定金额抵扣")
    void fixedAmountCoupon() {
        when(userCouponMapper.selectById(COUPON_ID)).thenReturn(coupon(USER_ID, 0, 1));
        when(couponTemplateMapper.selectById(2001L)).thenReturn(template(1, 3000L, 500L, null, 1));
        when(userCouponMapper.lock(COUPON_ID, USER_ID, ORDER_ID)).thenReturn(1);

        CouponLockResult result = couponService.lock(USER_ID, COUPON_ID, ORDER_ID, 4000L);

        assertThat(result.discountAmount()).isEqualTo(500L);
        assertThat(result.bearer()).isEqualTo(1);
        assertThat(result.userCouponId()).isEqualTo(COUPON_ID);
    }

    @Test
    @DisplayName("折扣券：按折扣率计算抵扣并四舍五入到分")
    void rateCoupon() {
        when(userCouponMapper.selectById(COUPON_ID)).thenReturn(coupon(USER_ID, 0, 1));
        when(couponTemplateMapper.selectById(2001L))
                .thenReturn(template(2, 3000L, null, new BigDecimal("0.80"), 2));
        when(userCouponMapper.lock(COUPON_ID, USER_ID, ORDER_ID)).thenReturn(1);

        CouponLockResult result = couponService.lock(USER_ID, COUPON_ID, ORDER_ID, 4000L);

        assertThat(result.discountAmount()).isEqualTo(800L);
        assertThat(result.bearer()).isEqualTo(2);
    }

    @Test
    @DisplayName("抵扣金额不超过商品金额")
    void discountNeverExceedsGoodsAmount() {
        when(userCouponMapper.selectById(COUPON_ID)).thenReturn(coupon(USER_ID, 0, 1));
        when(couponTemplateMapper.selectById(2001L)).thenReturn(template(1, 3000L, 9000L, null, 1));
        when(userCouponMapper.lock(COUPON_ID, USER_ID, ORDER_ID)).thenReturn(1);

        CouponLockResult result = couponService.lock(USER_ID, COUPON_ID, ORDER_ID, 3000L);

        assertThat(result.discountAmount()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("未达到门槛拒绝核销")
    void thresholdNotMet() {
        when(userCouponMapper.selectById(COUPON_ID)).thenReturn(coupon(USER_ID, 0, 1));
        when(couponTemplateMapper.selectById(2001L)).thenReturn(template(1, 3000L, 500L, null, 1));

        assertBizCode(() -> couponService.lock(USER_ID, COUPON_ID, ORDER_ID, 2999L),
                ErrorCode.COUPON_NOT_AVAILABLE);
        verify(userCouponMapper, never()).lock(COUPON_ID, USER_ID, ORDER_ID);
    }

    @Test
    @DisplayName("券不属于当前用户时拒绝")
    void couponOwnershipMismatch() {
        when(userCouponMapper.selectById(COUPON_ID)).thenReturn(coupon(999L, 0, 1));

        assertBizCode(() -> couponService.lock(USER_ID, COUPON_ID, ORDER_ID, 4000L),
                ErrorCode.COUPON_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("已使用或已锁定的券拒绝重复核销")
    void couponAlreadyUsed() {
        when(userCouponMapper.selectById(COUPON_ID)).thenReturn(coupon(USER_ID, 2, 1));

        assertBizCode(() -> couponService.lock(USER_ID, COUPON_ID, ORDER_ID, 4000L),
                ErrorCode.COUPON_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("过期券拒绝核销")
    void expiredCoupon() {
        UserCoupon expired = coupon(USER_ID, 0, 1);
        expired.setExpireAt(LocalDateTime.now().minusSeconds(1));
        when(userCouponMapper.selectById(COUPON_ID)).thenReturn(expired);

        assertBizCode(() -> couponService.lock(USER_ID, COUPON_ID, ORDER_ID, 4000L),
                ErrorCode.COUPON_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("模板停用时拒绝核销")
    void disabledTemplate() {
        when(userCouponMapper.selectById(COUPON_ID)).thenReturn(coupon(USER_ID, 0, 1));
        CouponTemplate disabled = template(1, 3000L, 500L, null, 1);
        disabled.setStatus(1);
        when(couponTemplateMapper.selectById(2001L)).thenReturn(disabled);

        assertBizCode(() -> couponService.lock(USER_ID, COUPON_ID, ORDER_ID, 4000L),
                ErrorCode.COUPON_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("并发抢占导致条件更新返回 0 时抛 COUPON_LOCK_FAILED")
    void concurrentLockFailure() {
        when(userCouponMapper.selectById(COUPON_ID)).thenReturn(coupon(USER_ID, 0, 1));
        when(couponTemplateMapper.selectById(2001L)).thenReturn(template(1, 3000L, 500L, null, 1));
        when(userCouponMapper.lock(COUPON_ID, USER_ID, ORDER_ID)).thenReturn(0);

        assertBizCode(() -> couponService.lock(USER_ID, COUPON_ID, ORDER_ID, 4000L),
                ErrorCode.COUPON_LOCK_FAILED);
    }

    @Test
    @DisplayName("折扣率为空时拒绝")
    void rateCouponWithoutRate() {
        when(userCouponMapper.selectById(COUPON_ID)).thenReturn(coupon(USER_ID, 0, 1));
        when(couponTemplateMapper.selectById(2001L)).thenReturn(template(2, 3000L, null, null, 1));
        when(userCouponMapper.lock(COUPON_ID, USER_ID, ORDER_ID)).thenReturn(1);

        assertBizCode(() -> couponService.lock(USER_ID, COUPON_ID, ORDER_ID, 4000L),
                ErrorCode.COUPON_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("释放失败抛 COUPON_STATE_INVALID")
    void releaseFailure() {
        when(userCouponMapper.release(COUPON_ID, ORDER_ID)).thenReturn(0);

        assertBizCode(() -> couponService.release(COUPON_ID, ORDER_ID),
                ErrorCode.COUPON_STATE_INVALID);
    }

    @Test
    @DisplayName("核销失败抛 COUPON_STATE_INVALID")
    void consumeFailure() {
        when(userCouponMapper.consume(COUPON_ID, ORDER_ID)).thenReturn(0);

        assertBizCode(() -> couponService.consume(COUPON_ID, ORDER_ID),
                ErrorCode.COUPON_STATE_INVALID);
    }

    @Test
    @DisplayName("退款回滚：未过期原路恢复，已过期落 5 状态")
    void rollbackBranches() {
        when(userCouponMapper.rollbackValid(COUPON_ID, ORDER_ID)).thenReturn(1);
        couponService.rollback(COUPON_ID, ORDER_ID);
        verify(userCouponMapper, never()).rollbackExpired(COUPON_ID, ORDER_ID);

        when(userCouponMapper.rollbackValid(COUPON_ID, ORDER_ID)).thenReturn(0);
        couponService.rollback(COUPON_ID, ORDER_ID);
        verify(userCouponMapper).rollbackExpired(COUPON_ID, ORDER_ID);
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }

    private UserCoupon coupon(long userId, int status, int templateIdSeed) {
        UserCoupon coupon = new UserCoupon();
        coupon.setId(COUPON_ID);
        coupon.setUserId(userId);
        coupon.setStatus(status);
        coupon.setTemplateId(2000L + templateIdSeed);
        coupon.setExpireAt(LocalDateTime.now().plusDays(1));
        return coupon;
    }

    private CouponTemplate template(int type, Long threshold, Long discountAmount,
                                    BigDecimal discountRate, int bearer) {
        CouponTemplate template = new CouponTemplate();
        template.setId(2001L);
        template.setCouponType(type);
        template.setThresholdAmount(threshold);
        template.setDiscountAmount(discountAmount);
        template.setDiscountRate(discountRate);
        template.setBearer(bearer);
        template.setStatus(0);
        return template;
    }
}
