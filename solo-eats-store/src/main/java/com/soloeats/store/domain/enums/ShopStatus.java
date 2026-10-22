package com.soloeats.store.domain.enums;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;

import java.util.Set;

/**
 * 门店状态是「老板意愿」，与营业时段规则分离：
 * 真正可下单 = 本状态为 OPEN 且当前时间落在任一营业时段内。
 *
 * <p>0休息 / 1营业 / 2打烊由商家自助切换；3停用只能由平台设置。
 */
public enum ShopStatus {

    RESTING(0, "休息"),
    OPEN(1, "营业"),
    CLOSED(2, "打烊"),
    DISABLED(3, "停用");

    private static final Set<Integer> MERCHANT_SWITCHABLE = Set.of(
            RESTING.code, OPEN.code, CLOSED.code);

    private final int code;
    private final String text;

    ShopStatus(int code, String text) {
        this.code = code;
        this.text = text;
    }

    public int code() {
        return code;
    }

    public String text() {
        return text;
    }

    public static ShopStatus fromCode(Integer code) {
        if (code != null) {
            for (ShopStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "未知门店状态: " + code);
    }

    public static String textOf(Integer code) {
        try {
            return fromCode(code).text;
        } catch (BizException ex) {
            return "";
        }
    }

    /** 商家端只允许在 0/1/2 之间切换，停用属于平台动作。 */
    public static boolean merchantSwitchable(Integer code) {
        return code != null && MERCHANT_SWITCHABLE.contains(code);
    }
}
