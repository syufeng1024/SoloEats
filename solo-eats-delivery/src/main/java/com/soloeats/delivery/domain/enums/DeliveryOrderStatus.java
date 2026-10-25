package com.soloeats.delivery.domain.enums;

public enum DeliveryOrderStatus {

    WAIT_ASSIGN(0, "待派单"),
    ASSIGNED(10, "已派单"),
    ACCEPTED(20, "已接单"),
    PICKED_UP(30, "已取货"),
    DELIVERING(40, "配送中"),
    DELIVERED(50, "已送达"),
    CANCELLED(70, "已取消"),
    REASSIGNED(71, "已改派");

    private final int code;
    private final String description;

    DeliveryOrderStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
