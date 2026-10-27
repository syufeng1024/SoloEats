package com.soloeats.settlement.service.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抽佣规则新建/编辑入参。
 *
 * <p>必填与范围校验统一落在 {@code CommissionServiceImpl}：
 * ruleScope 只允许 1~4；二/三/四级必须带 refId，全局级必须为空；
 * commissionRate 必须在 (0, 1] 之间；effectiveTo 为 null 表示长期有效。
 */
public record CommissionRuleSaveCommand(
        Integer ruleScope,
        Long refId,
        BigDecimal commissionRate,
        Long minAmount,
        Long maxAmount,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Integer priority,
        Integer status,
        String remark
) {
}
