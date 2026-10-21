package com.soloeats.order.domain.enums;

public enum OperatorType {

    USER(1),
    MERCHANT(2),
    RIDER(3),
    PLATFORM(4),
    SYSTEM(5);

    private final int code;

    OperatorType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
