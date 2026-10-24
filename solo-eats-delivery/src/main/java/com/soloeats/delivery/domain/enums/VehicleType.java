package com.soloeats.delivery.domain.enums;

public enum VehicleType {

    E_BIKE(1),
    MOTORCYCLE(2),
    BICYCLE(3),
    WALKING(4);

    private final int code;

    VehicleType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static boolean isValid(Integer code) {
        if (code == null) {
            return false;
        }
        for (VehicleType type : values()) {
            if (type.code == code) {
                return true;
            }
        }
        return false;
    }
}
