package com.soloeats.app.support;

import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.entity.OrderItem;
import com.soloeats.order.mapper.OrderItemMapper;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.product.service.ReviewOrderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 评价资格查询适配器。product 模块不依赖 order 实体，由 app 模块把
 * 订单与明细组装成只读快照传入。
 */
@Component
@RequiredArgsConstructor
public class ReviewOrderPortAdapter implements ReviewOrderPort {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public OrderItemSnapshot find(Long orderId, Long orderItemId) {
        if (orderId == null || orderItemId == null) {
            return null;
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return null;
        }
        OrderItem item = orderItemMapper.selectById(orderItemId);
        if (item == null || !Objects.equals(item.getOrderId(), orderId)) {
            return null;
        }
        return new OrderItemSnapshot(
                order.getId(),
                item.getId(),
                order.getUserId(),
                order.getShopId(),
                item.getSpuId(),
                item.getSkuId(),
                order.getStatus(),
                order.getCompletedAt()
        );
    }
}
