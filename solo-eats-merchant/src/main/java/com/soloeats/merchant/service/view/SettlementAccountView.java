package com.soloeats.merchant.service.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 结算账户视图。bankAccountNo 为脱敏值，禁止返回密文。
 */
public record SettlementAccountView(
        Long id,
        String accountName,
        String bankName,
        String bankBranch,
        String bankAccountNo,
        String alipayAccount,
        Long contractFileId,
        String contractFileUrl,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Integer isDefault,
        Integer status,
        LocalDateTime createdAt
) {
}
