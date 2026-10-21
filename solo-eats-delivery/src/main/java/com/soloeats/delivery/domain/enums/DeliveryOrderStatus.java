package com.soloeats.delivery.domain.enums;

public enum DeliveryOrderStatus {

    WAIT_ASSIGN(0),
    ASSIGNED(10),
    ACCEPTED(20),
    PICKED_UP(30),
    DELIVERING(40),
    DELIVERED(50),
    CANCELLED(70),
    REASSIGNED(71);

    private final int code;

    DeliveryOrderStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
