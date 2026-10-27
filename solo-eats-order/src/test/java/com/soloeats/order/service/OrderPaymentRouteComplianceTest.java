package com.soloeats.order.service;

import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.marketing.service.CouponService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderDiscount;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderDiscountMapper;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.product.service.StockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 双履约分流符合性用例。需求来源：规格书 8.1「支付成功后分流」、8.6「自取不生成配送单」、
 * 13 章「双履约闭环」。
 *
 * <p>这是 P0 主链路的分岔点，此前<b>完全没有测试覆盖</b>：
 * <ul>
 *   <li>平台配送单：必须生成配送单（进入派单），且不得生成取餐码；</li>
 *   <li>到店自取单：必须<b>不</b>生成配送单、<b>不</b>计配送费，改为生成 4 位取餐码；</li>
 *   <li>非待支付订单重复回调：完全幂等，不产生任何写操作。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrderPaymentRouteComplianceTest {

    private static final long ORDER_ID = 90001L;
    private static final long SHOP_ID = 1L;
    private static final long USER_ID = 1L;
    private static final long SKU_ID = 1001L;
    private static final long COUPON_ID = 3001L;
    private static final long DELIVERY_FEE = 500L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderDiscountMapper orderDiscountMapper;
    @Mock
    private OrderApplicationService orderApplicationService;
    @Mock
    private StockService stockService;
    @Mock
    private CouponService couponService;
    @Mock
    private DeliveryService deliveryService;

    @InjectMocks
    private OrderPaymentApplicationService orderPaymentApplicationService;

    @Test
    @DisplayName("配送单支付成功：生成配送单，不生成取餐码")
    void deliveryOrderCreatesDeliveryOrder() {
        when(orderMapper.selectById(ORDER_ID))
                .thenReturn(order(FulfillmentType.DELIVERY, OrderStatus.WAIT_PAY));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));
        when(orderDiscountMapper.selectOne(any())).thenReturn(null);

        orderPaymentApplicationService.handlePaySuccess(ORDER_ID);

        verify(deliveryService).createForOrder(eq(ORDER_ID), eq(SHOP_ID), any(), any(), any(),
                eq(DELIVERY_FEE));
        verify(orderMapper, never()).assignPickupCode(any(), anyString(), any(LocalDate.class));
    }

    @Test
    @DisplayName("自取单支付成功：不生成配送单、不计配送费，生成 4 位取餐码")
    void pickupOrderSkipsDeliveryAndAssignsPickupCode() {
        Order pickup = order(FulfillmentType.PICKUP, OrderStatus.WAIT_PAY);
        pickup.setDeliveryFee(0L);
        when(orderMapper.selectById(ORDER_ID)).thenReturn(pickup);
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));
        when(orderDiscountMapper.selectOne(any())).thenReturn(null);

        orderPaymentApplicationService.handlePaySuccess(ORDER_ID);

        verifyNoInteractions(deliveryService);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(orderMapper).assignPickupCode(eq(ORDER_ID), codeCaptor.capture(),
                any(LocalDate.class));
        assertThat(codeCaptor.getValue()).matches("[1-9]\\d{3}");
    }

    @Test
    @DisplayName("非待支付订单重复支付回调：完全幂等，不做任何写操作")
    void paySuccessOnNonWaitPayOrderIsNoop() {
        when(orderMapper.selectById(ORDER_ID))
                .thenReturn(order(FulfillmentType.DELIVERY, OrderStatus.PREPARING));

        orderPaymentApplicationService.handlePaySuccess(ORDER_ID);

        verifyNoInteractions(deliveryService, stockService, couponService);
        verify(orderApplicationService, never())
                .transit(any(), any(), any(), any(), any(), anyString());
        verify(orderMapper, never()).markPaid(any());
    }

    @Test
    @DisplayName("支付成功：确认库存、核销券、标记已支付，再分流履约")
    void paySuccessConfirmsStockAndConsumesCoupon() {
        when(orderMapper.selectById(ORDER_ID))
                .thenReturn(order(FulfillmentType.DELIVERY, OrderStatus.WAIT_PAY));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));
        when(orderDiscountMapper.selectOne(any())).thenReturn(couponDiscount());

        orderPaymentApplicationService.handlePaySuccess(ORDER_ID);

        verify(stockService).confirm(ORDER_ID, SHOP_ID, SKU_ID, 1);
        verify(couponService).consume(COUPON_ID, ORDER_ID);
        verify(orderMapper).markPaid(ORDER_ID);
        verify(orderApplicationService).transit(eq(ORDER_ID), eq(OrderEvent.PAY_SUCCESS),
                eq(FulfillmentType.DELIVERY), any(), any(), anyString());
    }

    @Test
    @DisplayName("支付超时：释放库存与优惠券，不生成配送单/取餐码")
    void payTimeoutReleasesReservedAssets() {
        when(orderMapper.selectById(ORDER_ID))
                .thenReturn(order(FulfillmentType.PICKUP, OrderStatus.WAIT_PAY));
        when(orderItemMapper.selectList(any())).thenReturn(List.of(item()));
        when(orderDiscountMapper.selectOne(any())).thenReturn(couponDiscount());

        orderPaymentApplicationService.handleTimeout(ORDER_ID);

        verify(stockService).release(ORDER_ID, SHOP_ID, SKU_ID, 1);
        verify(couponService).release(COUPON_ID, ORDER_ID);
        verifyNoInteractions(deliveryService);
        verify(orderMapper, never()).assignPickupCode(any(), anyString(), any(LocalDate.class));
        verify(orderApplicationService).transit(eq(ORDER_ID), eq(OrderEvent.PAY_TIMEOUT),
                eq(FulfillmentType.PICKUP), any(), any(), anyString());
    }

    private Order order(FulfillmentType type, OrderStatus status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("SO-90001");
        order.setUserId(USER_ID);
        order.setMerchantId(10L);
        order.setShopId(SHOP_ID);
        order.setFulfillmentType(type.getCode());
        order.setStatus(status.getCode());
        order.setPayAmount(3000L);
        order.setDeliveryFee(DELIVERY_FEE);
        order.setReceiverName("张三");
        order.setReceiverPhone("13800000000");
        order.setReceiverAddress("北京市朝阳区望京 SOHO T1");
        return order;
    }

    private OrderItem item() {
        OrderItem item = new OrderItem();
        item.setId(7001L);
        item.setOrderId(ORDER_ID);
        item.setShopId(SHOP_ID);
        item.setSkuId(SKU_ID);
        item.setQuantity(1);
        item.setPayableAmount(3000L);
        return item;
    }

    private OrderDiscount couponDiscount() {
        OrderDiscount discount = new OrderDiscount();
        discount.setId(1L);
        discount.setOrderId(ORDER_ID);
        discount.setDiscountType(1);
        discount.setSourceId(COUPON_ID);
        discount.setBearer(1);
        discount.setAmount(200L);
        return discount;
    }
}
