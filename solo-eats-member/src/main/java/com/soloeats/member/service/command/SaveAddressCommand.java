package com.soloeats.member.service.command;

import java.math.BigDecimal;

/**
 * 新增 / 修改收货地址。isDefault 为 null 表示创建时沿用现有逻辑
 * （首条自动默认，其余默认非默认），修改时保持原值不变。
 */
public record SaveAddressCommand(
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String address,
        String houseNumber,
        BigDecimal lng,
        BigDecimal lat,
        String tag,
        Boolean isDefault
) {
}
