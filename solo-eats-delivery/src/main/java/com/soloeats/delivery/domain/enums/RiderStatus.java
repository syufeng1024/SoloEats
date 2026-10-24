package com.soloeats.delivery.domain.enums;

public enum RiderStatus {

    NORMAL(0),
    SUSPENDED(1),
    BLACKLISTED(2);

    private final int code;

    RiderStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static boolean isValid(Integer code) {
        if (code == null) {
            return false;
        }
        for (RiderStatus status : values()) {
            if (status.code == code) {
                return true;
            }
        }
        return false;
    }
}
