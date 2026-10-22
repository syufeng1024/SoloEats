package com.soloeats.store.service.command;

import java.math.BigDecimal;

/**
 * 下单前配送范围校验。goodsAmount 为商品小计（分），不含配送费与打包费。
 * fulfillmentType：1平台配送 2到店自取。
 */
public record DeliveryCheckCommand(
        BigDecimal lng,
        BigDecimal lat,
        Long goodsAmount,
        Integer fulfillmentType
) {
}
