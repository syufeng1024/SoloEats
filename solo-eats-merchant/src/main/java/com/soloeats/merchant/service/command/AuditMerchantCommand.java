package com.soloeats.merchant.service.command;

import java.math.BigDecimal;

/**
 * 审核通过入参。initialPassword 只在本次请求内使用，不落日志、不回显。
 */
public record AuditMerchantCommand(
        BigDecimal commissionRate,
        Integer merchantLevel,
        String adminUsername,
        String initialPassword,
        String remark,
        Long operatorId,
        String operatorName
) {
}
