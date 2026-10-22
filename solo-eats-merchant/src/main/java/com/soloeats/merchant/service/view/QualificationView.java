package com.soloeats.merchant.service.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record QualificationView(
        Long id,
        Integer qualType,
        Long fileId,
        String fileUrl,
        String fileName,
        LocalDate expireAt,
        Integer status,
        String auditRemark,
        LocalDateTime createdAt
) {
}
