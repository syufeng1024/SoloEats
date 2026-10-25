package com.soloeats.app.service;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.enums.DeliveryOrderStatus;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.delivery.service.DeliveryTrackService;
import com.soloeats.delivery.service.RiderIncomeService;
import com.soloeats.delivery.service.view.DeliveryActionView;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OperatorType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.service.DeliveryFulfillmentApplicationService;
import com.soloeats.order.service.OrderApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiderDeliveryFacadeTest {

    private static final long DELIVERY_ID = 2L;
    private static final long ORDER_ID = 3L;
    private static final long RIDER_ID = 11L;

    @Mock
    private DeliveryService deliveryService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderApplicationService orderApplicationService;
    @Mock
    private DeliveryTrackService deliveryTrackService;
    @Mock
    private RiderIncomeService riderIncomeService;

    private RiderDeliveryFacade facade;

    @BeforeEach
    void setUp() {
        // 用真实订单侧应用服务验证跨域编排，订单状态流转由 OrderStateMachine 语义驱动
        DeliveryFulfillmentApplicationService fulfillment =
                new DeliveryFulfillmentApplicationService(orderMapper, orderApplicationService, deliveryService);
        facade = new RiderDeliveryFacade(
                deliveryService, fulfillment, deliveryTrackService, riderIncomeService);
    }

    @Test
    @DisplayName("取货：订单推进 RIDER_PICKUP，配送单返回 40")
    void pickupAdvancesOrderAndDelivery() {
        when(deliveryService.getById(DELIVERY_ID)).thenReturn(
                delivery(DeliveryOrderStatus.ACCEPTED),
                delivery(DeliveryOrderStatus.DELIVERING));
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.WAIT_RIDER_PICKUP));
        when(deliveryService.pickup(ORDER_ID, RIDER_ID))
                .thenReturn(delivery(DeliveryOrderStatus.DELIVERING));
        when(orderApplicationService.transit(eq(ORDER_ID), eq(OrderEvent.RIDER_PICKUP),
                eq(FulfillmentType.DELIVERY), eq(OperatorType.RIDER), eq(RIDER_ID), anyString()))
                .thenReturn(OrderStatus.DELIVERING);

        DeliveryActionView view = facade.pickup(DELIVERY_ID, RIDER_ID);

        assertThat(view.deliveryId()).isEqualTo(DELIVERY_ID);
        assertThat(view.orderId()).isEqualTo(ORDER_ID);
        assertThat(view.status()).isEqualTo(40);
        verify(deliveryService).pickup(ORDER_ID, RIDER_ID);
        verify(orderApplicationService).transit(eq(ORDER_ID), eq(OrderEvent.RIDER_PICKUP),
                eq(FulfillmentType.DELIVERY), eq(OperatorType.RIDER), eq(RIDER_ID), anyString());
    }

    @Test
    @DisplayName("送达：订单 DELIVERED -> AUTO_COMPLETE(60)，配送单返回 50")
    void deliveredCompletesOrder() {
        stubDeliveringOrder();
        stubDeliveredTransitions();
        when(deliveryService.getById(DELIVERY_ID)).thenReturn(
                delivery(DeliveryOrderStatus.DELIVERING),
                delivery(DeliveryOrderStatus.DELIVERED));

        DeliveryActionView view = facade.delivered(
                DELIVERY_ID, RIDER_ID, new BigDecimal("116.49"), new BigDecimal("39.99"), null);

        assertThat(view.status()).isEqualTo(50);
        InOrder inOrder = inOrder(deliveryService, orderApplicationService);
        inOrder.verify(deliveryService).deliver(ORDER_ID, RIDER_ID);
        inOrder.verify(orderApplicationService).transit(eq(ORDER_ID), eq(OrderEvent.RIDER_DELIVERED),
                eq(FulfillmentType.DELIVERY), eq(OperatorType.RIDER), eq(RIDER_ID), anyString());
        inOrder.verify(orderApplicationService).transit(eq(ORDER_ID), eq(OrderEvent.AUTO_COMPLETE),
                eq(FulfillmentType.DELIVERY), eq(OperatorType.SYSTEM), isNull(), anyString());
        verify(orderMapper).markCompleted(eq(ORDER_ID), any(LocalDateTime.class));
        verify(deliveryTrackService).report(eq(RIDER_ID), any());
    }

    @Test
    @DisplayName("送达只触发一次骑手收入结算")
    void deliveredSettlesIncomeOnce() {
        stubDeliveringOrder();
        stubDeliveredTransitions();
        when(deliveryService.getById(DELIVERY_ID)).thenReturn(
                delivery(DeliveryOrderStatus.DELIVERING),
                delivery(DeliveryOrderStatus.DELIVERED));

        facade.delivered(DELIVERY_ID, RIDER_ID, null, null, null);

        verify(riderIncomeService, times(1)).settle(DELIVERY_ID);
        verify(deliveryTrackService, never()).report(any(), any());
    }

    @Test
    @DisplayName("订单状态不匹配：异常上抛，配送推进与结算均不提交")
    void orderTransitionFailurePropagates() {
        when(deliveryService.getById(DELIVERY_ID))
                .thenReturn(delivery(DeliveryOrderStatus.DELIVERING));
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.PREPARING));

        assertThatThrownBy(() -> facade.delivered(
                DELIVERY_ID, RIDER_ID, new BigDecimal("116.49"), new BigDecimal("39.99"), null))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());

        verify(deliveryService, never()).deliver(any(), any());
        verify(orderApplicationService, never()).transit(any(), any(), any(), any(), any(), any());
        verify(riderIncomeService, never()).settle(any());
        verify(deliveryTrackService, never()).report(any(), any());
    }

    @Test
    @DisplayName("重复送达不再推进订单，但仍补结算并返回 50")
    void repeatedDeliveredSkipsOrderTransit() {
        when(deliveryService.getById(DELIVERY_ID))
                .thenReturn(delivery(DeliveryOrderStatus.DELIVERED));

        DeliveryActionView view = facade.delivered(DELIVERY_ID, RIDER_ID, null, null, null);

        assertThat(view.status()).isEqualTo(50);
        verify(orderMapper, never()).selectById(any());
        verify(orderApplicationService, never()).transit(any(), any(), any(), any(), any(), any());
        verify(riderIncomeService, times(1)).settle(DELIVERY_ID);
    }

    private void stubDeliveringOrder() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(OrderStatus.DELIVERING));
        when(deliveryService.deliver(ORDER_ID, RIDER_ID))
                .thenReturn(delivery(DeliveryOrderStatus.DELIVERED));
    }

    private void stubDeliveredTransitions() {
        when(orderApplicationService.transit(eq(ORDER_ID), eq(OrderEvent.RIDER_DELIVERED),
                eq(FulfillmentType.DELIVERY), eq(OperatorType.RIDER), eq(RIDER_ID), anyString()))
                .thenReturn(OrderStatus.DELIVERED);
        when(orderApplicationService.transit(eq(ORDER_ID), eq(OrderEvent.AUTO_COMPLETE),
                eq(FulfillmentType.DELIVERY), eq(OperatorType.SYSTEM), isNull(), anyString()))
                .thenReturn(OrderStatus.COMPLETED);
        when(orderMapper.markCompleted(eq(ORDER_ID), any(LocalDateTime.class))).thenReturn(1);
    }

    private DeliveryOrder delivery(DeliveryOrderStatus status) {
        DeliveryOrder delivery = new DeliveryOrder();
        delivery.setId(DELIVERY_ID);
        delivery.setOrderId(ORDER_ID);
        delivery.setRiderId(RIDER_ID);
        delivery.setStatus(status.getCode());
        return delivery;
    }

    private Order order(OrderStatus status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setStatus(status.getCode());
        order.setFulfillmentType(FulfillmentType.DELIVERY.getCode());
        order.setVersion(0);
        return order;
    }
}
