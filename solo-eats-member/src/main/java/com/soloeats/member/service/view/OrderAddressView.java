package com.soloeats.member.service.view;

import java.math.BigDecimal;

/**
 * 下单时的收货地址快照。receiverPhone 为解密后的归一化明文，只供订单快照落库，
 * 禁止直接透出到任何接口响应。
 */
public record OrderAddressView(
        Long id,
        String receiverName,
        String receiverPhone,
        String address,
        BigDecimal lng,
        BigDecimal lat
) {
}
