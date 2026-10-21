package com.soloeats.product.domain.enums;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;

public enum StockLockStatus {

    LOCKED(0),
    CONFIRMED(1),
    RELEASED(2),
    REFUNDED(3);

    private final int code;

    StockLockStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static StockLockStatus fromCode(int code) {
        for (StockLockStatus value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new BizException(ErrorCode.STOCK_STATE_INVALID);
    }
}
