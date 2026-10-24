package com.soloeats.member.domain.enums;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;

/**
 * 钱包流水方向：1 收入 / 2 支出。金额列恒为正数，方向由本枚举区分。
 */
public enum WalletDirection {

    IN(1),
    OUT(2);

    private final int code;

    WalletDirection(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static WalletDirection fromCode(Integer code) {
        if (code != null) {
            for (WalletDirection direction : values()) {
                if (direction.code == code) {
                    return direction;
                }
            }
        }
        throw new BizException(ErrorCode.WALLET_AMOUNT_INVALID, "未知钱包流水方向: " + code);
    }
}
