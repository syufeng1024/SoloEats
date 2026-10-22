package com.soloeats.merchant.service.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商家详情。legalIdCard 为脱敏值，禁止返回密文或明文。
 */
public record MerchantDetailView(
        Long id,
        String merchantNo,
        String merchantName,
        String brandName,
        Long categoryId,
        String contactName,
        String contactPhone,
        String contactEmail,
        String businessLicenseNo,
        String legalPerson,
        String legalIdCard,
        String province,
        String city,
        String district,
        String address,
        Integer status,
        BigDecimal commissionRate,
        Integer level,
        Long auditBy,
        LocalDateTime auditAt,
        String auditRemark,
        LocalDateTime openedAt,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<QualificationView> qualifications,
        List<AuditLogView> auditLogs
) {
}
