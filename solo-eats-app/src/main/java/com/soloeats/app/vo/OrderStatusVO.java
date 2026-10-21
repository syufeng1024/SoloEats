package com.soloeats.app.vo;

import com.soloeats.order.domain.enums.OrderStatus;

public record OrderStatusVO(
        Long orderId,
        Integer status,
        String statusName
) {

    public static OrderStatusVO of(Long orderId, OrderStatus status) {
        return new OrderStatusVO(orderId, status.getCode(), status.getDescription());
    }
}
