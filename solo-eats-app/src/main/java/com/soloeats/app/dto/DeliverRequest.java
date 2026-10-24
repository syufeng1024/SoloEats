package com.soloeats.app.dto;

import java.math.BigDecimal;

/**
 * 确认送达可选留证：坐标用于补最后一个轨迹点，photoFileId 只记日志。
 */
public record DeliverRequest(
        BigDecimal lng,
        BigDecimal lat,
        Long photoFileId
) {
}
