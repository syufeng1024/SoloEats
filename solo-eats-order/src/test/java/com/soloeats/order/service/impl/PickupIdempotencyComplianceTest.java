package com.soloeats.order.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.service.OrderApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 取餐码核销幂等与售后期起点用例。需求来源：规格书 8.6、8.8、11.2「自取」。
 *
 * <p>断言要点：
 * <ol>
 *   <li>同一订单重复核销被状态位拦截，不产生第二次状态流转、不重复写结算冻结时间；</li>
 *   <li>核销成功即写 {@code settle_eligible_at = 核销时刻 + 售后期天数}（默认 7 天），
 *       这是自取单售后期与结算冻结的唯一起点；</li>
 *   <li>取餐码错误时不得推进任何状态。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class PickupIdempotencyComplianceTest {

    private static final long MERCHANT_ID = 10L;
    private static final long ORDER_ID = 92001L;
    private static final String PICKUP_CODE = "A1234";
    private static final long AFTER_SALES_DAYS = 7L;

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderApplicationService orderApplicationService;

    @InjectMocks
    private MerchantPickupServiceImpl merchantPickupService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(merchantPickupService, "afterSalesDays", AFTER_SALES_DAYS);
    }

    @Test
    @DisplayName("重复核销：第二次被状态位拦截，只发生一轮状态流转")
    void secondVerifyIsRejectedWithoutSideEffects() {
        when(orderMapper.selectById(ORDER_ID))
                .thenReturn(pickupOrder(OrderStatus.WAIT_USER_PICKUP),
                        pickupOrder(OrderStatus.COMPLETED));
        stubTransitChain();
        when(orderMapper.markCompleted(eq(ORDER_ID), any(LocalDateTime.class))).thenReturn(1);

        OrderStatus first = merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, PICKUP_CODE);
        assertThat(first).isEqualTo(OrderStatus.COMPLETED);

        assertThatThrownBy(
                () -> merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, PICKUP_CODE))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());

        // 一次核销只允许 PICKUP_VERIFY + PICKUP_COMPLETE 两次流转，重复核销不得加一次
        verify(orderApplicationService, times(2))
                .transit(any(), any(), any(), any(), any(), anyString());
        verify(orderMapper, times(1)).markCompleted(eq(ORDER_ID), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("核销成功写入结算冻结时间 = 核销时刻 + 售后期天数（默认 7 天）")
    void verifyWritesAfterSalesFreezeWindow() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(pickupOrder(OrderStatus.WAIT_USER_PICKUP));
        stubTransitChain();
        when(orderMapper.markCompleted(eq(ORDER_ID), any(LocalDateTime.class))).thenReturn(1);

        LocalDateTime before = LocalDateTime.now();
        merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, PICKUP_CODE);

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderMapper).markCompleted(eq(ORDER_ID), captor.capture());
        LocalDateTime settleEligibleAt = captor.getValue();

        assertThat(settleEligibleAt)
                .isAfterOrEqualTo(before.plusDays(AFTER_SALES_DAYS))
                .isBefore(before.plusDays(AFTER_SALES_DAYS).plusMinutes(1));
    }

    @Test
    @DisplayName("已取餐(51)订单不可再次核销")
    void verifyRejectsAlreadyPickedUpOrder() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(pickupOrder(OrderStatus.PICKED_UP));

        assertThatThrownBy(
                () -> merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, PICKUP_CODE))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());

        verify(orderApplicationService, never()).transit(any(), any(), any(), any(), any(), anyString());
        verify(orderMapper, never()).markCompleted(any(), any());
    }

    @Test
    @DisplayName("取餐码错误时不推进状态、不写结算冻结时间")
    void wrongPickupCodeKeepsOrderUntouched() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(pickupOrder(OrderStatus.WAIT_USER_PICKUP));

        assertThatThrownBy(
                () -> merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, "9999"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());

        verify(orderApplicationService, never()).transit(any(), any(), any(), any(), any(), anyString());
        verify(orderMapper, never()).markCompleted(any(), any());
    }

    private void stubTransitChain() {
        when(orderApplicationService.transit(ORDER_ID, OrderEvent.PICKUP_VERIFY,
                FulfillmentType.PICKUP, com.soloeats.order.domain.enums.OperatorType.MERCHANT,
                MERCHANT_ID, "PICKUP_VERIFY:" + ORDER_ID))
                .thenReturn(OrderStatus.PICKED_UP);
        when(orderApplicationService.transit(ORDER_ID, OrderEvent.PICKUP_COMPLETE,
                FulfillmentType.PICKUP, com.soloeats.order.domain.enums.OperatorType.SYSTEM,
                null, "PICKUP_COMPLETE:" + ORDER_ID))
                .thenReturn(OrderStatus.COMPLETED);
    }

    private Order pickupOrder(OrderStatus status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("SO-92001");
        order.setMerchantId(MERCHANT_ID);
        order.setShopId(1L);
        order.setUserId(1L);
        order.setFulfillmentType(FulfillmentType.PICKUP.getCode());
        order.setStatus(status.getCode());
        order.setPickupCode(PICKUP_CODE);
        order.setPayAmount(2000L);
        return order;
    }
}
