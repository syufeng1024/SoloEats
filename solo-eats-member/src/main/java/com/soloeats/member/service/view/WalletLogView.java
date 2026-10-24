package com.soloeats.member.service.view;

import com.soloeats.member.domain.entity.MemberWalletLog;

import java.time.LocalDateTime;

/** 钱包流水展示体，金额单位分；不对外返回内部幂等键 request_id。 */
public record WalletLogView(
        Long id,
        String bizType,
        Long bizId,
        Integer direction,
        long amount,
        long beforeBalance,
        long afterBalance,
        String remark,
        String operatorName,
        LocalDateTime createdAt
) {

    public static WalletLogView from(MemberWalletLog log) {
        return new WalletLogView(
                log.getId(),
                log.getBizType(),
                log.getBizId(),
                log.getDirection(),
                nullSafe(log.getAmount()),
                nullSafe(log.getBeforeBalance()),
                nullSafe(log.getAfterBalance()),
                log.getRemark(),
                log.getOperatorName(),
                log.getCreatedAt());
    }

    private static long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
