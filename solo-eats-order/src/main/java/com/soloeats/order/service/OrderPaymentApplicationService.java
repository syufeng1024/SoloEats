package com.soloeats.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.delivery.service.DeliveryService;
import com.soloeats.marketing.service.CouponService;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderDiscount;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.domain.enums.FulfillmentType;
import com.soloeats.order.domain.enums.OperatorType;
import com.soloeats.order.domain.enums.OrderEvent;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderDiscountMapper;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.product.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 支付成功 / 支付超时的订单侧编排。
 * 状态流转、库存确认/释放、优惠券核销/释放、支付状态更新在同一事务内。
 */
@Service
@RequiredArgsConstructor
public class OrderPaymentApplicationService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderDiscountMapper orderDiscountMapper;
    private final OrderApplicationService orderApplicationService;
    private final StockService stockService;
    private final CouponService couponService;
    private final DeliveryService deliveryService;

    @Transactional(rollbackFor = Exception.class)
    public void handlePaySuccess(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getStatus(), OrderStatus.WAIT_PAY.getCode())) {
            return;
        }

        orderApplicationService.transit(orderId, OrderEvent.PAY_SUCCESS,
                FulfillmentType.fromCode(order.getFulfillmentType()),
                OperatorType.SYSTEM, null, "PAY_SUCCESS:" + orderId);

        for (OrderItem item : loadItems(orderId)) {
            stockService.confirm(orderId, order.getShopId(), item.getSkuId(), item.getQuantity());
        }

        OrderDiscount discount = loadCouponDiscount(orderId);
        if (discount != null) {
            couponService.consume(discount.getSourceId(), orderId);
        }
        orderMapper.markPaid(orderId);

        if (Objects.equals(order.getFulfillmentType(), FulfillmentType.DELIVERY.getCode())) {
            deliveryService.createForOrder(
                    orderId,
                    order.getShopId(),
                    order.getReceiverAddress(),
                    order.getReceiverLng(),
                    order.getReceiverLat(),
                    order.getDeliveryFee()
            );
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleTimeout(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getStatus(), OrderStatus.WAIT_PAY.getCode())) {
            return;
        }

        orderApplicationService.transit(orderId, OrderEvent.PAY_TIMEOUT,
                FulfillmentType.fromCode(order.getFulfillmentType()),
                OperatorType.SYSTEM, null, "PAY_TIMEOUT:" + orderId);

        for (OrderItem item : loadItems(orderId)) {
            stockService.release(orderId, order.getShopId(), item.getSkuId(), item.getQuantity());
        }

        OrderDiscount discount = loadCouponDiscount(orderId);
        if (discount != null) {
            couponService.release(discount.getSourceId(), orderId);
        }
    }

    private List<OrderItem> loadItems(Long orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
    }

    private OrderDiscount loadCouponDiscount(Long orderId) {
        return orderDiscountMapper.selectOne(new LambdaQueryWrapper<OrderDiscount>()
                .eq(OrderDiscount::getOrderId, orderId)
                .eq(OrderDiscount::getDiscountType, 1)
                .last("LIMIT 1"));
    }
}
