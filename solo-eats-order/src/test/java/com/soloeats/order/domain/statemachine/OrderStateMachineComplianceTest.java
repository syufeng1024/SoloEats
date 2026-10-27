package com.soloeats.order.domain.statemachine;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单状态机符合性用例（真实状态机实例，非 mock）。需求来源：规格书 8.1、8.9、8.2。
 *
 * <p>本类全部为<b>防回归</b>用例，断言的是当前已达成的事实，不应出现红项。
 *
 * <p><b>TODO(D-04 待决策)</b>：规格书 8.9 把「已退款」列为独立状态，但当前实现沿用
 * {@code OrderStateMachineTest#refundFullSuccessCancelsNonTerminal} 的既有契约——
 * 未完成订单全额退款落到 {@code CANCELLED}；已完成订单的售后期退款<b>不改订单状态</b>，
 * 只由 {@code OrderMapper#markAfterSalesRefunded}（{@code WHERE id=? AND status=60}）
 * 回写 {@code refund_status=2} 与 {@code refunded_amount}，退款凭证以
 * {@code t_refund_order} / {@code t_refund_item} 为准。
 * 两处口径的收敛方案由产品裁定（A：追加 REFUNDED 状态并同步改既有断言；
 * B：维持 CANCELLED + 前端按 refund_status 渲染「已退款」文案）。
 */
class OrderStateMachineComplianceTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    @DisplayName("配送单主链路：待支付→待接单→备餐中→待取货→配送中→已送达→已完成")
    void deliveryHappyPathReachesCompleted() {
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
    }

    @Test
    @DisplayName("自取单与配送单共用状态机但在备餐完成处分叉，不引入新状态")
    void pickupChainForksAfterPreparing() {
        assertThat(stateMachine.next(OrderStatus.PREPARING, OrderEvent.PREPARE_DONE,
                FulfillmentType.DELIVERY)).isEqualTo(OrderStatus.WAIT_RIDER_PICKUP);
        assertThat(stateMachine.next(OrderStatus.PREPARING, OrderEvent.PREPARE_DONE,
                FulfillmentType.PICKUP)).isEqualTo(OrderStatus.WAIT_USER_PICKUP);
        assertThat(stateMachine.next(OrderStatus.WAIT_USER_PICKUP, OrderEvent.PICKUP_VERIFY,
                FulfillmentType.PICKUP)).isEqualTo(OrderStatus.PICKED_UP);
        assertThat(stateMachine.next(OrderStatus.PICKED_UP, OrderEvent.PICKUP_COMPLETE,
                FulfillmentType.PICKUP)).isEqualTo(OrderStatus.COMPLETED);
    }

    /**
     * TODO(D-04 待决策)：未完成订单全额退款落到「已取消」是既有契约
     * （见 {@code OrderStateMachineTest#refundFullSuccessCancelsNonTerminal}），
     * 与规格书 8.9 的独立「已退款」状态存在口径差异，此处按现状固化以防回归。
     */
    @Test
    @DisplayName("合同锁定：未完成订单全额退款落到「已取消」")
    void refundFullSuccessOnNonTerminalCancelsOrder() {
        assertThat(stateMachine.next(OrderStatus.WAIT_ACCEPT,
                OrderEvent.REFUND_FULL_SUCCESS, FulfillmentType.DELIVERY))
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(stateMachine.next(OrderStatus.PREPARING,
                OrderEvent.REFUND_FULL_SUCCESS, FulfillmentType.DELIVERY))
                .isEqualTo(OrderStatus.CANCELLED);
    }

    /**
     * TODO(D-04 待决策)：已完成订单不允许状态流转，售后期退款由
     * {@code OrderMapper#markAfterSalesRefunded}（{@code WHERE id=? AND status=60}）
     * 只回写 {@code refund_status=2}，订单状态保持 COMPLETED。
     */
    @Test
    @DisplayName("已完成订单不接受退款流转：状态机拒绝，售后由 refund_status 回写承接")
    void completedOrderRefundRejectedByStateMachine() {
        assertThat(stateMachine.canTransit(OrderStatus.COMPLETED,
                OrderEvent.REFUND_FULL_SUCCESS, FulfillmentType.DELIVERY)).isFalse();
        assertThatThrownBy(() -> stateMachine.next(OrderStatus.COMPLETED,
                OrderEvent.REFUND_FULL_SUCCESS, FulfillmentType.DELIVERY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());
    }

    /**
     * TODO(D-04 待决策)：自取单与配送单同口径——已完成即终态，状态机拒绝流转，
     * 售后走 {@code refund_status} 回写，不把订单回退成「已取消」。
     */
    @Test
    @DisplayName("已完成的取单同样由状态机拒绝退款流转")
    void completedPickupOrderRefundRejectedByStateMachine() {
        assertThat(stateMachine.canTransit(OrderStatus.COMPLETED,
                OrderEvent.REFUND_FULL_SUCCESS, FulfillmentType.PICKUP)).isFalse();
        assertThatThrownBy(() -> stateMachine.next(OrderStatus.COMPLETED,
                OrderEvent.REFUND_FULL_SUCCESS, FulfillmentType.PICKUP))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());
    }

    @Test
    @DisplayName("非法流转抛 ORDER_STATUS_INVALID（待支付不能直接骑手取货）")
    void illegalTransitionRejected() {
        assertThatThrownBy(() -> stateMachine.next(OrderStatus.WAIT_PAY,
                OrderEvent.RIDER_PICKUP, FulfillmentType.DELIVERY))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID.getCode());
    }

    @Test
    @DisplayName("已完成订单不接受用户确认收货等前向事件")
    void completedRejectsForwardEvent() {
        assertThat(stateMachine.canTransit(OrderStatus.COMPLETED,
                OrderEvent.USER_CONFIRM, FulfillmentType.DELIVERY)).isFalse();
    }

    @Test
    @DisplayName("自取链路不允许骑手取货事件（履约类型隔离）")
    void pickupTypeRejectsRiderEvents() {
        assertThat(stateMachine.canTransit(OrderStatus.WAIT_USER_PICKUP,
                OrderEvent.RIDER_PICKUP, FulfillmentType.PICKUP)).isFalse();
    }
}
