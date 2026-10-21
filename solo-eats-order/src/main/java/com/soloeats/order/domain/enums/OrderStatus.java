package com.soloeats.order.domain.enums;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;

public enum OrderStatus {

    WAIT_PAY(0, "待支付"),
    WAIT_ACCEPT(10, "待接单"),
    PREPARING(20, "备餐中"),
    WAIT_RIDER_PICKUP(30, "待骑手取货"),
    WAIT_USER_PICKUP(31, "待用户取餐"),
    DELIVERING(40, "配送中"),
    DELIVERED(50, "已送达"),
    PICKED_UP(51, "已取餐"),
    COMPLETED(60, "已完成"),
    CANCELLED(70, "已取消");

    private final int code;
    private final String description;

    OrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    public static OrderStatus fromCode(int code) {
        for (OrderStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
    }
}
