package com.soloeats.payment.domain.enums;

public enum PaymentStatus {

    INIT(0),
    PAYING(10),
    SUCCESS(20),
    FAIL(30),
    CLOSED(40),
    REFUNDING(50),
    REFUNDED(60),
    PARTIAL_REFUNDED(61);

    private final int code;

    PaymentStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
