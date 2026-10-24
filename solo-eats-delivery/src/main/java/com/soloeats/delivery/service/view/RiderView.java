package com.soloeats.delivery.service.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 后台骑手列表项。手机号只返回掩码，不含身份证明文/密文。
 */
public record RiderView(
        Long id,
        String riderNo,
        String realName,
        String phoneMask,
        Integer vehicleType,
        Integer certStatus,
        Integer status,
        Integer onlineStatus,
        BigDecimal rating,
        BigDecimal onTimeRate,
        Integer totalOrders,
        LocalDateTime lastOnlineAt,
        LocalDateTime createdAt
) {
}
