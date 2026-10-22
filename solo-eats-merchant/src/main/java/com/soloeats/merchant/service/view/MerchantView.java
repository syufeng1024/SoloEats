package com.soloeats.merchant.service.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MerchantView(
        Long id,
        String merchantNo,
        String merchantName,
        String brandName,
        Long categoryId,
        String contactName,
        String contactPhone,
        String contactEmail,
        String province,
        String city,
        String district,
        String address,
        Integer status,
        BigDecimal commissionRate,
        Integer level,
        LocalDateTime auditAt,
        String auditRemark,
        LocalDateTime createdAt
) {
}
