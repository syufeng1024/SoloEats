package com.soloeats.report.domain.enums;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;

import java.util.Locale;

/**
 * 埋点事件白名单。新增事件类型必须同时更新漏斗聚合口径。
 */
public enum TrackEventType {

    /** 首页曝光。 */
    HOME_EXPOSE,
    /** 店铺/商品点击。 */
    SHOP_CLICK,
    /** 加入购物车。 */
    ADD_CART,
    /** 提交订单。 */
    ORDER_SUBMIT,
    /** 支付成功。 */
    PAY_SUCCESS;

    public static TrackEventType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "eventType 不能为空");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "eventType 仅支持 HOME_EXPOSE / SHOP_CLICK / ADD_CART / ORDER_SUBMIT / PAY_SUCCESS");
        }
    }
}
