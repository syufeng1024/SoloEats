package com.soloeats.marketing.service.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券模板新建/编辑入参。
 *
 * <p>编辑只消费 name/thresholdAmount/discountAmount/discountRate/totalQty/
 * validStart/validEnd/validDays；couponType 与 bearer 在编辑时被忽略，
 * 传了也不会落库（A1-4 的可编辑字段白名单）。
 *
 * <p>issuedQty 与 status 不在本入参内：前者只由发券条件更新维护，后者走
 * {@code updateStatus} 独立端点。
 *
 * @param couponType 1满减 2折扣 3无门槛，新建必填
 * @param bearer     1平台 2商家，新建为空时取 1
 */
public record CouponTemplateSaveCommand(
        String name,
        Integer couponType,
        Long thresholdAmount,
        Long discountAmount,
        BigDecimal discountRate,
        Integer bearer,
        Integer totalQty,
        LocalDateTime validStart,
        LocalDateTime validEnd,
        Integer validDays
) {
}
