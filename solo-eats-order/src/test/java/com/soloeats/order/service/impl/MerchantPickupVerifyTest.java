package com.soloeats.order.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OperatorType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.order.service.OrderApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantPickupVerifyTest {

    private static final long MERCHANT_ID = 10L;
    private static final long OTHER_MERCHANT_ID = 11L;
    private static final long ORDER_ID = 92001L;
    private static final String PICKUP_CODE = "A1234";

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderApplicationService orderApplicationService;

    @InjectMocks
    private MerchantPickupServiceImpl merchantPickupService;

    @Test
    @DisplayName("核销：订单不存在返回 ORDER_NOT_FOUND")
    void verifyMissingOrder() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        assertBizCode(() -> merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, PICKUP_CODE),
                ErrorCode.ORDER_NOT_FOUND);
        verifyNoInteractions(orderApplicationService);
    }

    @Test
    @DisplayName("核销：非本商家订单返回 AUTH_FORBIDDEN")
    void verifyRejectsOtherMerchantOrder() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(2, OrderStatus.WAIT_USER_PICKUP.getCode()));

        assertBizCode(() -> merchantPickupService.verifyPickup(ORDER_ID, OTHER_MERCHANT_ID, PICKUP_CODE),
                ErrorCode.AUTH_FORBIDDEN);
        verifyNoInteractions(orderApplicationService);
    }

    @Test
    @DisplayName("核销：配送单返回 PARAM_INVALID，不推进状态")
    void verifyRejectsDeliveryOrder() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(1, OrderStatus.WAIT_USER_PICKUP.getCode()));

        assertBizCode(() -> merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, PICKUP_CODE),
                ErrorCode.PARAM_INVALID);
        assertThatThrownBy(() -> merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, PICKUP_CODE))
                .isInstanceOf(BizException.class)
                .hasMessage("该订单不是自取订单");
        verifyNoInteractions(orderApplicationService);
    }

    @Test
    @DisplayName("核销：状态非 31 返回 ORDER_STATUS_INVALID")
    void verifyRejectsNonWaitPickupStatus() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(2, OrderStatus.PICKED_UP.getCode()));

        assertBizCode(() -> merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, PICKUP_CODE),
                ErrorCode.ORDER_STATUS_INVALID);
        assertThatThrownBy(() -> merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, PICKUP_CODE))
                .isInstanceOf(BizException.class)
                .hasMessage("订单当前状态不可核销");
        verifyNoInteractions(orderApplicationService);
    }

    @Test
    @DisplayName("核销：取餐码不匹配返回 PARAM_INVALID，提示不含正确码")
    void verifyRejectsWrongPickupCodeWithoutLeakingIt() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(2, OrderStatus.WAIT_USER_PICKUP.getCode()));

        assertThatThrownBy(() -> merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, "9999"))
                .isInstanceOf(BizException.class)
                .hasMessage("取餐码不正确")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(PICKUP_CODE));
        verifyNoInteractions(orderApplicationService);
    }

    @Test
    @DisplayName("核销：31 核销成功，经状态机流转到 51")
    void verifySucceedsAndTransitsToPickedUp() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(order(2, OrderStatus.WAIT_USER_PICKUP.getCode()));
        when(orderApplicationService.transit(
                ORDER_ID,
                OrderEvent.PICKUP_VERIFY,
                FulfillmentType.PICKUP,
                OperatorType.MERCHANT,
                MERCHANT_ID,
                "PICKUP_VERIFY:" + ORDER_ID
        )).thenReturn(OrderStatus.PICKED_UP);

        OrderStatus result = merchantPickupService.verifyPickup(ORDER_ID, MERCHANT_ID, " " + PICKUP_CODE + " ");

        assertThat(result).isEqualTo(OrderStatus.PICKED_UP);
        assertThat(result.getCode()).isEqualTo(51);
        verify(orderApplicationService).transit(
                ORDER_ID,
                OrderEvent.PICKUP_VERIFY,
                FulfillmentType.PICKUP,
                OperatorType.MERCHANT,
                MERCHANT_ID,
                "PICKUP_VERIFY:" + ORDER_ID
        );
    }

    private Order order(int fulfillmentType, int status) {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setOrderNo("SO" + ORDER_ID);
        order.setUserId(1L);
        order.setMerchantId(MERCHANT_ID);
        order.setShopId(1L);
        order.setFulfillmentType(fulfillmentType);
        order.setStatus(status);
        order.setPickupCode(PICKUP_CODE);
        return order;
    }

    private void assertBizCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
                               ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
