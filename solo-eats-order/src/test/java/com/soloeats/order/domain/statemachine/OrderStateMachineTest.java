package com.soloeats.order.domain.statemachine;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    @DisplayName("初始状态为待支付")
    void initialStatusIsWaitPay() {
        assertThat(stateMachine.initialStatus()).isEqualTo(OrderStatus.WAIT_PAY);
    }

    @Test
    @DisplayName("配送单完整正向链路：待支付 -> 已完成")
    void deliveryHappyPath() {
        OrderStatus status = OrderStatus.WAIT_PAY;

        status = stateMachine.next(status, OrderEvent.PAY_SUCCESS, FulfillmentType.DELIVERY);
        assertThat(status).isEqualTo(OrderStatus.WAIT_ACCEPT);

        status = stateMachine.next(status, OrderEvent.MERCHANT_ACCEPT, FulfillmentType.DELIVERY);
        assertThat(status).isEqualTo(OrderStatus.PREPARING);

        status = stateMachine.next(status, OrderEvent.PREPARE_DONE, FulfillmentType.DELIVERY);
        assertThat(status).isEqualTo(OrderStatus.WAIT_RIDER_PICKUP);

        status = stateMachine.next(status, OrderEvent.RIDER_PICKUP, FulfillmentType.DELIVERY);
        assertThat(status).isEqualTo(OrderStatus.DELIVERING);

        status = stateMachine.next(status, OrderEvent.RIDER_DELIVERED, FulfillmentType.DELIVERY);
        assertThat(status).isEqualTo(OrderStatus.DELIVERED);

        status = stateMachine.next(status, OrderEvent.USER_CONFIRM, FulfillmentType.DELIVERY);
        assertThat(status).isEqualTo(OrderStatus.COMPLETED);
        assertThat(status.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("自取单完整正向链路：备餐完成进入待取餐")
    void pickupHappyPath() {
        OrderStatus status = OrderStatus.PREPARING;

        status = stateMachine.next(status, OrderEvent.PREPARE_DONE, FulfillmentType.PICKUP);
        assertThat(status).isEqualTo(OrderStatus.WAIT_USER_PICKUP);

        status = stateMachine.next(status, OrderEvent.PICKUP_VERIFY, FulfillmentType.PICKUP);
        assertThat(status).isEqualTo(OrderStatus.PICKED_UP);

        status = stateMachine.next(status, OrderEvent.PICKUP_COMPLETE, FulfillmentType.PICKUP);
        assertThat(status).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("同一事件按履约类型分流：备餐完成在配送/自取下落到不同状态")
    void prepareDoneBranchesByFulfillmentType() {
        assertThat(stateMachine.next(OrderStatus.PREPARING, OrderEvent.PREPARE_DONE,
                FulfillmentType.DELIVERY)).isEqualTo(OrderStatus.WAIT_RIDER_PICKUP);
        assertThat(stateMachine.next(OrderStatus.PREPARING, OrderEvent.PREPARE_DONE,
                FulfillmentType.PICKUP)).isEqualTo(OrderStatus.WAIT_USER_PICKUP);
    }

    @Test
    @DisplayName("配送侧事件不能驱动自取单")
    void deliveryEventRejectedOnPickupOrder() {
        assertThatThrownBy(() -> stateMachine.next(
                OrderStatus.PREPARING, OrderEvent.RIDER_PICKUP, FulfillmentType.PICKUP))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());
    }

    @ParameterizedTest(name = "终态 {0} 不接受任何常规事件")
    @EnumSource(value = OrderStatus.class, names = {"COMPLETED", "CANCELLED"})
    void terminalStatusRejectsEvents(OrderStatus terminal) {
        assertThatThrownBy(() -> stateMachine.next(terminal, OrderEvent.PAY_SUCCESS, null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("待支付订单不能直接接单")
    void cannotAcceptBeforePayment() {
        assertThatThrownBy(() -> stateMachine.next(
                OrderStatus.WAIT_PAY, OrderEvent.MERCHANT_ACCEPT, FulfillmentType.DELIVERY))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("强制完成仅对非终态生效")
    void forceCompleteOnlyFromNonTerminal() {
        assertThat(stateMachine.next(OrderStatus.DELIVERING, OrderEvent.FORCE_COMPLETE, null))
                .isEqualTo(OrderStatus.COMPLETED);
        assertThatThrownBy(() -> stateMachine.next(
                OrderStatus.COMPLETED, OrderEvent.FORCE_COMPLETE, null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("全额退款成功把非终态订单置为已取消，终态订单拒绝")
    void refundFullSuccessCancelsNonTerminal() {
        assertThat(stateMachine.next(OrderStatus.WAIT_ACCEPT, OrderEvent.REFUND_FULL_SUCCESS, null))
                .isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> stateMachine.next(
                OrderStatus.CANCELLED, OrderEvent.REFUND_FULL_SUCCESS, null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("平台取消可中断配送中订单")
    void platformCancelInterruptsDelivering() {
        assertThat(stateMachine.next(OrderStatus.DELIVERING, OrderEvent.PLATFORM_CANCEL, null))
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("取餐超时按事件分流为完成或取消")
    void pickupTimeoutBranches() {
        assertThat(stateMachine.next(OrderStatus.WAIT_USER_PICKUP,
                OrderEvent.PICKUP_TIMEOUT_COMPLETE, FulfillmentType.PICKUP))
                .isEqualTo(OrderStatus.COMPLETED);
        assertThat(stateMachine.next(OrderStatus.WAIT_USER_PICKUP,
                OrderEvent.PICKUP_TIMEOUT_CANCEL, FulfillmentType.PICKUP))
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("null 入参按非法状态处理")
    void nullArgumentsRejected() {
        assertThatThrownBy(() -> stateMachine.next(null, OrderEvent.PAY_SUCCESS, null))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> stateMachine.next(OrderStatus.WAIT_PAY, null, null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("canTransit 对合法/非法流转分别返回 true/false")
    void canTransitReflectsTable() {
        assertThat(stateMachine.canTransit(
                OrderStatus.WAIT_PAY, OrderEvent.PAY_SUCCESS, FulfillmentType.DELIVERY)).isTrue();
        assertThat(stateMachine.canTransit(
                OrderStatus.WAIT_PAY, OrderEvent.RIDER_DELIVERED, FulfillmentType.DELIVERY)).isFalse();
    }

    @Test
    @DisplayName("状态码与终态判定")
    void statusCodeMapping() {
        assertThat(OrderStatus.fromCode(0)).isEqualTo(OrderStatus.WAIT_PAY);
        assertThat(OrderStatus.fromCode(70)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(OrderStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.DELIVERED.isTerminal()).isFalse();
        assertThatThrownBy(() -> OrderStatus.fromCode(999))
                .isInstanceOf(BizException.class);
    }
}
