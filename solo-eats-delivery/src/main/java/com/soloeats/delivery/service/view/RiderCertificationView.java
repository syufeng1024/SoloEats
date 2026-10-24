package com.soloeats.delivery.service.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 认证记录。不返回 id_card_cipher，也不返回明文。
 */
public record RiderCertificationView(
        Long id,
        String realName,
        LocalDate healthCertExpireAt,
        Integer status,
        Long auditBy,
        LocalDateTime auditAt,
        String auditRemark,
        LocalDateTime createdAt
) {
}
