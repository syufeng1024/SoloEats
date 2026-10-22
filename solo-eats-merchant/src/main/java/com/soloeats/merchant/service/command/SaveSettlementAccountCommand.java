package com.soloeats.merchant.service.command;

import java.time.LocalDate;

/**
 * 结算账户登记入参。bankAccountNo 为明文，服务层加密后写入 bank_account_no_cipher。
 */
public record SaveSettlementAccountCommand(
        String accountName,
        String bankName,
        String bankBranch,
        String bankAccountNo,
        String alipayAccount,
        Long contractFileId,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
