package com.soloeats.delivery.service.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 运费试算入参。goodsAmount 单位分。
 *
 * @param fulfillmentType 1平台配送 2到店自取，自取直接返回 0
 * @param deliverAt       预计送达时间，为空按当前时间判断时段加价
 */
public record FreightEstimateCommand(
        Long shopId,
        BigDecimal lng,
        BigDecimal lat,
        Long goodsAmount,
        BigDecimal weightKg,
        Integer floor,
        LocalDateTime deliverAt,
        Integer fulfillmentType
) {
}
