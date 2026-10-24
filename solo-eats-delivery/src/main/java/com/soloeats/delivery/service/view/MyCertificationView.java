package com.soloeats.delivery.service.view;

import java.time.LocalDate;

/**
 * 骑手端「我的认证」。未创建档案时 certStatus 返回 0（未提交）。
 */
public record MyCertificationView(
        Long riderId,
        String riderNo,
        String realName,
        Integer certStatus,
        String certRemark,
        LocalDate healthCertExpireAt,
        Integer vehicleType
) {
}
