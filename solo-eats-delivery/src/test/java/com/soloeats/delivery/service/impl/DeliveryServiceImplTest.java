package com.soloeats.delivery.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import com.soloeats.delivery.domain.entity.RiderIncome;
import com.soloeats.delivery.domain.enums.DeliveryOrderStatus;
import com.soloeats.delivery.mapper.DeliveryOrderMapper;
import com.soloeats.delivery.mapper.RiderIncomeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceImplTest {

    private static final long ORDER_ID = 90001L;
    private static final long RIDER_ID = 77L;

    @Mock
    private DeliveryOrderMapper deliveryOrderMapper;
    @Mock
    private RiderIncomeMapper riderIncomeMapper;

    @InjectMocks
    private DeliveryServiceImpl deliveryService;

    @Test
    @DisplayName("首次取餐完成时生成配送单，配送费为空按 0 处理")
    void createDeliveryForOrder() {
        when(deliveryOrderMapper.findByOrderId(ORDER_ID)).thenReturn(null);

        deliveryService.createForOrder(ORDER_ID, 1L, "shop-1", "幸福路 1 号", null, null, null);

        ArgumentCaptor<DeliveryOrder> captor = ArgumentCaptor.forClass(DeliveryOrder.class);
        verify(deliveryOrderMapper).insert(captor.capture());
        DeliveryOrder saved = captor.getValue();
        assertThat(saved.getDeliveryNo()).startsWith("DL");
        assertThat(saved.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(saved.getStatus()).isEqualTo(DeliveryOrderStatus.WAIT_ASSIGN.getCode());
        assertThat(saved.getDeliveryFee()).isZero();
        assertThat(saved.getDispatchCount()).isZero();
    }

    @Test
    @DisplayName("重复创建配送单保持幂等，不重复插入")
    void createDeliveryIsIdempotent() {
        when(deliveryOrderMapper.findByOrderId(ORDER_ID)).thenReturn(delivery(DeliveryOrderStatus.ACCEPTED));

        deliveryService.createForOrder(ORDER_ID, 1L, "shop-1", "幸福路 1 号", null, null, 500L);

        verify(deliveryOrderMapper, never()).insert(any(DeliveryOrder.class));
    }

    @Test
    @DisplayName("同一骑手重复抢单直接返回当前配送单")
    void acceptIdempotentForSameRider() {
        DeliveryOrder accepted = delivery(DeliveryOrderStatus.ACCEPTED);
        accepted.setRiderId(RIDER_ID);
        when(deliveryOrderMapper.findByOrderId(ORDER_ID)).thenReturn(accepted);

        DeliveryOrder result = deliveryService.accept(ORDER_ID, RIDER_ID);

        assertThat(result.getRiderId()).isEqualTo(RIDER_ID);
        verify(deliveryOrderMapper, never()).accept(any(), any());
    }

    @Test
    @DisplayName("抢单条件更新失败且已被其他骑手占用时抛 DISPATCH_TAKEN")
    void acceptTakenByOtherRider() {
        DeliveryOrder taken = delivery(DeliveryOrderStatus.ACCEPTED);
        taken.setRiderId(88L);
        when(deliveryOrderMapper.findByOrderId(ORDER_ID)).thenReturn(taken);
        when(deliveryOrderMapper.accept(ORDER_ID, RIDER_ID)).thenReturn(0);

        assertBizCode(() -> deliveryService.accept(ORDER_ID, RIDER_ID), ErrorCode.DISPATCH_TAKEN);
    }

    @Test
    @DisplayName("抢单条件更新返回 0 且无人占用时抛 DELIVERY_STATE_INVALID")
    void acceptStateInvalid() {
        DeliveryOrder waiting = delivery(DeliveryOrderStatus.WAIT_ASSIGN);
        when(deliveryOrderMapper.findByOrderId(ORDER_ID)).thenReturn(waiting);
        when(deliveryOrderMapper.accept(ORDER_ID, RIDER_ID)).thenReturn(0);

        assertBizCode(() -> deliveryService.accept(ORDER_ID, RIDER_ID), ErrorCode.DELIVERY_STATE_INVALID);
    }

    @Test
    @DisplayName("取货成功返回最新配送单")
    void pickupSuccess() {
        DeliveryOrder picked = delivery(DeliveryOrderStatus.PICKED_UP);
        picked.setRiderId(RIDER_ID);
        when(deliveryOrderMapper.pickup(ORDER_ID, RIDER_ID)).thenReturn(1);
        when(deliveryOrderMapper.findByOrderId(ORDER_ID)).thenReturn(picked);

        DeliveryOrder result = deliveryService.pickup(ORDER_ID, RIDER_ID);

        assertThat(result.getStatus()).isEqualTo(DeliveryOrderStatus.PICKED_UP.getCode());
    }

    @Test
    @DisplayName("非本单骑手取货时抛 DELIVERY_RIDER_MISMATCH")
    void pickupByWrongRider() {
        DeliveryOrder other = delivery(DeliveryOrderStatus.ACCEPTED);
        other.setRiderId(88L);
        when(deliveryOrderMapper.pickup(ORDER_ID, RIDER_ID)).thenReturn(0);
        when(deliveryOrderMapper.findByOrderId(ORDER_ID)).thenReturn(other);

        assertBizCode(() -> deliveryService.pickup(ORDER_ID, RIDER_ID), ErrorCode.DELIVERY_RIDER_MISMATCH);
    }

    @Test
    @DisplayName("妥投成功按配送费生成骑手收入明细")
    void deliverCreatesRiderIncome() {
        DeliveryOrder delivering = delivery(DeliveryOrderStatus.DELIVERING);
        delivering.setRiderId(RIDER_ID);
        delivering.setDeliveryFee(600L);
        DeliveryOrder delivered = delivery(DeliveryOrderStatus.DELIVERED);
        delivered.setRiderId(RIDER_ID);
        delivered.setDeliveryFee(600L);
        when(deliveryOrderMapper.findByOrderId(ORDER_ID)).thenReturn(delivering, delivered);
        when(deliveryOrderMapper.delivered(ORDER_ID, RIDER_ID)).thenReturn(1);

        DeliveryOrder result = deliveryService.deliver(ORDER_ID, RIDER_ID);

        assertThat(result.getStatus()).isEqualTo(DeliveryOrderStatus.DELIVERED.getCode());
        ArgumentCaptor<RiderIncome> captor = ArgumentCaptor.forClass(RiderIncome.class);
        verify(riderIncomeMapper).insert(captor.capture());
        RiderIncome income = captor.getValue();
        assertThat(income.getRiderId()).isEqualTo(RIDER_ID);
        assertThat(income.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(income.getBaseFee()).isEqualTo(600L);
        assertThat(income.getTotalAmount()).isEqualTo(600L);
        assertThat(income.getStatus()).isZero();
    }

    @Test
    @DisplayName("重复妥投不重复生成骑手收入")
    void deliverIdempotent() {
        DeliveryOrder delivered = delivery(DeliveryOrderStatus.DELIVERED);
        delivered.setRiderId(RIDER_ID);
        when(deliveryOrderMapper.findByOrderId(ORDER_ID)).thenReturn(delivered);

        deliveryService.deliver(ORDER_ID, RIDER_ID);

        verify(riderIncomeMapper, never()).insert(any(RiderIncome.class));
        verify(deliveryOrderMapper, never()).delivered(any(), any());
    }

    private DeliveryOrder delivery(DeliveryOrderStatus status) {
        DeliveryOrder delivery = new DeliveryOrder();
        delivery.setId(1L);
        delivery.setOrderId(ORDER_ID);
        delivery.setShopId(1L);
        delivery.setStatus(status.getCode());
        return delivery;
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
