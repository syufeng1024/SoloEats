package com.soloeats.order.domain.enums;

public enum RefundStatus {

    APPLIED(0),
    REFUNDING(20),
    SUCCESS(30),
    FAILED(31),
    CANCELLED(40);

    private final int code;

    RefundStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
