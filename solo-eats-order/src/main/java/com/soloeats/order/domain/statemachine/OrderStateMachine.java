package com.soloeats.order.domain.statemachine;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单状态机。所有订单状态变更必须经过 next() 校验。
 * 直接 UPDATE t_order.status 的代码一律视为缺陷。
 */
@Component
public class OrderStateMachine {

    private record Key(OrderStatus from, OrderEvent event, FulfillmentType type) {
    }

    private static final Map<Key, OrderStatus> TRANSITIONS = new HashMap<>();

    static {
        register(OrderStatus.WAIT_PAY, OrderEvent.PAY_SUCCESS, null, OrderStatus.WAIT_ACCEPT);
        register(OrderStatus.WAIT_PAY, OrderEvent.CANCEL, null, OrderStatus.CANCELLED);
        register(OrderStatus.WAIT_PAY, OrderEvent.PAY_TIMEOUT, null, OrderStatus.CANCELLED);

        register(OrderStatus.WAIT_ACCEPT, OrderEvent.MERCHANT_ACCEPT, null, OrderStatus.PREPARING);
        register(OrderStatus.WAIT_ACCEPT, OrderEvent.MERCHANT_REJECT, null, OrderStatus.CANCELLED);
        register(OrderStatus.WAIT_ACCEPT, OrderEvent.USER_CANCEL, null, OrderStatus.CANCELLED);

        register(OrderStatus.PREPARING, OrderEvent.PREPARE_DONE, FulfillmentType.DELIVERY,
                OrderStatus.WAIT_RIDER_PICKUP);
        register(OrderStatus.WAIT_RIDER_PICKUP, OrderEvent.RIDER_PICKUP, FulfillmentType.DELIVERY,
                OrderStatus.DELIVERING);
        register(OrderStatus.DELIVERING, OrderEvent.RIDER_DELIVERED, FulfillmentType.DELIVERY,
                OrderStatus.DELIVERED);
        register(OrderStatus.DELIVERED, OrderEvent.USER_CONFIRM, FulfillmentType.DELIVERY,
                OrderStatus.COMPLETED);
        register(OrderStatus.DELIVERED, OrderEvent.AUTO_COMPLETE, FulfillmentType.DELIVERY,
                OrderStatus.COMPLETED);

        register(OrderStatus.PREPARING, OrderEvent.PREPARE_DONE, FulfillmentType.PICKUP,
                OrderStatus.WAIT_USER_PICKUP);
        register(OrderStatus.WAIT_USER_PICKUP, OrderEvent.PICKUP_VERIFY, FulfillmentType.PICKUP,
                OrderStatus.PICKED_UP);
        register(OrderStatus.PICKED_UP, OrderEvent.PICKUP_COMPLETE, FulfillmentType.PICKUP,
                OrderStatus.COMPLETED);
        register(OrderStatus.PICKED_UP, OrderEvent.AUTO_COMPLETE, FulfillmentType.PICKUP,
                OrderStatus.COMPLETED);
        register(OrderStatus.WAIT_USER_PICKUP, OrderEvent.PICKUP_TIMEOUT_COMPLETE, FulfillmentType.PICKUP,
                OrderStatus.COMPLETED);
        register(OrderStatus.WAIT_USER_PICKUP, OrderEvent.PICKUP_TIMEOUT_CANCEL, FulfillmentType.PICKUP,
                OrderStatus.CANCELLED);
    }

    private static void register(OrderStatus from, OrderEvent event,
                                 FulfillmentType type, OrderStatus to) {
        TRANSITIONS.put(new Key(from, event, type), to);
    }

    public OrderStatus initialStatus() {
        return OrderStatus.WAIT_PAY;
    }

    public OrderStatus next(OrderStatus from, OrderEvent event, FulfillmentType type) {
        if (from == null || event == null) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        }
        if (event == OrderEvent.FORCE_COMPLETE && !from.isTerminal()) {
            return OrderStatus.COMPLETED;
        }
        if (event == OrderEvent.REFUND_FULL_SUCCESS && !from.isTerminal()) {
            return OrderStatus.CANCELLED;
        }
        if (event == OrderEvent.PLATFORM_CANCEL && !from.isTerminal()) {
            return OrderStatus.CANCELLED;
        }

        OrderStatus to = TRANSITIONS.get(new Key(from, event, type));
        if (to == null) {
            to = TRANSITIONS.get(new Key(from, event, null));
        }
        if (to == null) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
        }
        return to;
    }

    public boolean canTransit(OrderStatus from, OrderEvent event, FulfillmentType type) {
        try {
            next(from, event, type);
            return true;
        } catch (BizException ex) {
            return false;
        }
    }
}
