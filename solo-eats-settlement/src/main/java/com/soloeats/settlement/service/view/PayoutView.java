package com.soloeats.settlement.service.view;

import java.time.LocalDateTime;

/**
 * 提现单视图。amount 单位为分。
 */
public record PayoutView(
        Long id,
        String payoutNo,
        Integer subjectType,
        Long subjectId,
        Long amount,
        Integer channel,
        Integer status,
        String auditRemark,
        Long auditBy,
        LocalDateTime auditAt,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
}
