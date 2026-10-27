package com.soloeats.settlement.service.view;

import com.soloeats.settlement.domain.entity.FundFlow;

import java.time.LocalDateTime;

/**
 * 平台资金流水视图。amount 单位为分；balanceAfter 恒为 null（平台无统一账户余额）。
 */
public record FundFlowView(
        Long id,
        Integer flowType,
        String bizType,
        Long bizId,
        Integer direction,
        Long amount,
        Long balanceAfter,
        String remark,
        LocalDateTime createdAt
) {

    public static FundFlowView of(FundFlow flow) {
        return new FundFlowView(
                flow.getId(),
                flow.getFlowType(),
                flow.getBizType(),
                flow.getBizId(),
                flow.getDirection(),
                flow.getAmount(),
                flow.getBalanceAfter(),
                flow.getRemark(),
                flow.getCreatedAt()
        );
    }
}
