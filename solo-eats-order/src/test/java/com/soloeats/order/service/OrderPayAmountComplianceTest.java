package com.soloeats.order.service;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.service.CouponLockResult;
import com.soloeats.marketing.service.CouponService;
import com.soloeats.member.service.MemberAddressService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.statemachine.OrderStateMachine;
import com.soloeats.order.mapper.OrderDiscountMapper;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.mapper.OrderStatusLogMapper;
import com.soloeats.order.service.command.CreateOrderCommand;
import com.soloeats.order.service.result.CreateOrderResult;
import com.soloeats.product.domain.entity.Sku;
import com.soloeats.product.service.SkuService;
import com.soloeats.product.service.StockService;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实付金额下限（≥ 0.01 元）符合性用例。需求来源：规格书 9.8「保底」、11.2「下单」。
 *
 * <p>不变量：<b>任何</b>成功创建的订单，{@code payAmount ≥ 1} 分；
 * 下限判断必须覆盖打包费、配送费、优惠后的最终值，且与"是否使用券"无关。
 *
 * <p><b>修复验证型用例</b>：{@code zeroAmountOrderWithoutCouponRejected} 在当前实现下预期失败
 * （下限校验被写在 {@code couponId != null} 分支内，无券 0 元单可建单成功）。
 */
@ExtendWith(MockitoExtension.class)
class OrderPayAmountComplianceTest {

    private static final long USER_ID = 7L;
    private static final long MERCHANT_ID = 1L;
    private static final long SHOP_ID = 1L;
    private static final long SKU_ID = 1001L;
    private static final long ORDER_ID = 1000L;
    private static final long COUPON_ID = 3001L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderDiscountMapper orderDiscountMapper;
    @Mock
    private OrderStatusLogMapper orderStatusLogMapper;
    @Mock
    private OrderStateMachine orderStateMachine;
    @Mock
    private SkuService skuService;
    @Mock
    private StockService stockService;
    @Mock
    private CouponService couponService;
    @Mock
    private MemberAddressService memberAddressService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OrderApplicationService orderApplicationService;

    @Test
    @DisplayName("无券 0 元单必须在下单时拒绝，不得产生不可支付的僵尸订单")
    void zeroAmountOrderWithoutCouponRejected() {
        stubSku(0L);

        assertBizCode(() -> orderApplicationService.createOrder(
                pickupCommand(null, 0L), "it-zero"), ErrorCode.PARAM_INVALID);

        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("券把实付抵到 0 元时拒绝下单")
    void couponZeroingPayAmountRejected() {
        stubSku(100L);
        stubCoupon(100L);
        stubInsertAndUpdate();

        assertBizCode(() -> orderApplicationService.createOrder(
                pickupCommand(COUPON_ID, 0L), "it-coupon-zero"), ErrorCode.PARAM_INVALID);
    }

    @Test
    @DisplayName("下限按 商品 + 打包 + 配送 − 优惠 的最终值判断：实付 1 分放行")
    void floorCountsPackagingAndDeliveryFee() {
        stubSku(100L);
        stubCoupon(599L);
        stubInsertAndUpdate();

        CreateOrderResult result = orderApplicationService.createOrder(
                deliveryCommand(COUPON_ID, 200L, 300L), "it-floor-pass");

        assertThat(result.payAmount()).isEqualTo(1L);
        assertThat(result.packagingFee()).isEqualTo(200L);
        assertThat(result.deliveryFee()).isEqualTo(300L);
        assertThat(result.discountAmount()).isEqualTo(599L);
    }

    @Test
    @DisplayName("实付算到 0 分时拒绝下单（下限 0.01 元不可被 1 分钱击穿）")
    void oneCentAboveFloorRejected() {
        stubSku(100L);
        stubCoupon(600L);
        stubInsertAndUpdate();

        assertBizCode(() -> orderApplicationService.createOrder(
                deliveryCommand(COUPON_ID, 200L, 300L), "it-floor-fail"),
                ErrorCode.PARAM_INVALID);
    }

    @Test
    @DisplayName("自取单不把配送费计入实付，也不计入下限基数")
    void pickupOrderDropsDeliveryFeeBeforeFloorCheck() {
        stubSku(300L);
        stubCoupon(200L);
        stubInsertAndUpdate();

        // 请求里故意传 deliveryFee=500（见 pickupCommand），服务端必须忽略
        CreateOrderResult result = orderApplicationService.createOrder(
                pickupCommand(COUPON_ID, 0L), "it-pickup-fee");

        assertThat(result.deliveryFee()).isZero();
        assertThat(result.payAmount()).isEqualTo(100L);
    }

    private void stubSku(long price) {
        Sku sku = new Sku();
        sku.setId(SKU_ID);
        sku.setShopId(SHOP_ID);
        sku.setSpuId(10L);
        sku.setSkuName("招牌牛肉面");
        sku.setPrice(price);
        when(skuService.getRequired(SKU_ID, SHOP_ID)).thenReturn(sku);
    }

    private void stubCoupon(long discount) {
        when(couponService.lock(anyLong(), anyLong(), anyLong(), anyLong()))
                .thenReturn(new CouponLockResult(COUPON_ID, 5001L, discount, 1));
    }

    /**
     * 用券路径：订单行先落库，折扣后的 payAmount 通过 updateById 回写。
     * updateById 设为 lenient —— 保底校验发生在回写之前（规格书 9.7「最终计算一次」），
     * 被保底拦下的用例根本走不到回写，严格桩会误报 UnnecessaryStubbingException。
     */
    private void stubInsertAndUpdate() {
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(ORDER_ID);
            return 1;
        });
        lenient().when(orderMapper.updateById(any(Order.class))).thenReturn(1);
    }

    private CreateOrderCommand pickupCommand(Long couponId, Long packagingFee) {
        return new CreateOrderCommand(
                USER_ID, MERCHANT_ID, SHOP_ID, FulfillmentType.PICKUP.getCode(),
                couponId, packagingFee, 500L,
                null, null, null, null, null, null,
                "13800000000", "自取", List.of(new CreateOrderCommand.ItemCommand(SKU_ID, 1)));
    }

    private CreateOrderCommand deliveryCommand(Long couponId, Long packagingFee, Long deliveryFee) {
        return new CreateOrderCommand(
                USER_ID, MERCHANT_ID, SHOP_ID, FulfillmentType.DELIVERY.getCode(),
                couponId, packagingFee, deliveryFee,
                "张三", "13800000000", "北京市朝阳区望京 SOHO T1", null, null, null,
                null, "少放辣", List.of(new CreateOrderCommand.ItemCommand(SKU_ID, 1)));
    }

    private void assertBizCode(ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
