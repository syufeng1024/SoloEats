package com.soloeats.order.service;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OperatorType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 骑手履约编排：接单、取货、送达。
 * 送达时订单走 DELIVERED -> COMPLETED 两步状态流转，并写 completed_at / settle_eligible_at。
 */
@Service
@RequiredArgsConstructor
public class DeliveryFulfillmentApplicationService {

    private final OrderMapper orderMapper;
    private final OrderApplicationService orderApplicationService;
    private final DeliveryService deliveryService;

    @Value("${app.order.after-sales-days:7}")
    private long afterSalesDays;

    @Transactional(rollbackFor = Exception.class)
    public OrderStatus acceptRider(Long orderId, Long riderId) {
        Order order = requireOrder(orderId);
        assertDeliveryOrder(order);
        if (!Objects.equals(order.getStatus(), OrderStatus.WAIT_RIDER_PICKUP.getCode())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "只有待取货订单可以骑手接单");
        }
        deliveryService.accept(orderId, riderId);
        return OrderStatus.WAIT_RIDER_PICKUP;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderStatus pickup(Long orderId, Long riderId) {
        Order order = requireOrder(orderId);
        assertDeliveryOrder(order);
        if (!Objects.equals(order.getStatus(), OrderStatus.WAIT_RIDER_PICKUP.getCode())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "只有待取货订单可以取货");
        }
        deliveryService.pickup(orderId, riderId);
        return orderApplicationService.transit(orderId, OrderEvent.RIDER_PICKUP,
                FulfillmentType.DELIVERY, OperatorType.RIDER, riderId, "RIDER_PICKUP:" + orderId);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderStatus delivered(Long orderId, Long riderId) {
        Order order = requireOrder(orderId);
        assertDeliveryOrder(order);
        if (!Objects.equals(order.getStatus(), OrderStatus.DELIVERING.getCode())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "只有配送中订单可以送达");
        }

        deliveryService.deliver(orderId, riderId);
        orderApplicationService.transit(orderId, OrderEvent.RIDER_DELIVERED,
                FulfillmentType.DELIVERY, OperatorType.RIDER, riderId, "RIDER_DELIVERED:" + orderId);
        OrderStatus completed = orderApplicationService.transit(orderId, OrderEvent.AUTO_COMPLETE,
                FulfillmentType.DELIVERY, OperatorType.SYSTEM, null, "AUTO_COMPLETE:" + orderId);

        int rows = orderMapper.markCompleted(orderId, LocalDateTime.now().plusDays(afterSalesDays));
        if (rows != 1) {
            throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED);
        }
        return completed;
    }

    private Order requireOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void assertDeliveryOrder(Order order) {
        if (!Objects.equals(order.getFulfillmentType(), FulfillmentType.DELIVERY.getCode())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "自取订单不进入骑手配送");
        }
    }
}
