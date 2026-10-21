package com.soloeats.order.domain.enums;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;

public enum FulfillmentType {

    DELIVERY(1, "配送"),
    PICKUP(2, "自取");

    private final int code;
    private final String description;

    FulfillmentType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static FulfillmentType fromCode(int code) {
        for (FulfillmentType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new BizException(ErrorCode.ORDER_STATUS_INVALID);
    }
}
