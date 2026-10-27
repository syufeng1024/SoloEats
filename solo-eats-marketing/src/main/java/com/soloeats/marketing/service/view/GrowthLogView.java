package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.MemberGrowthLog;

import java.time.LocalDateTime;

/**
 * 成长值明细。growth 可负，beforeValue/afterValue 是变更前后的近 365 天合计。
 */
public record GrowthLogView(
        Long id,
        Integer source,
        Long bizId,
        Integer growth,
        Integer beforeValue,
        Integer afterValue,
        LocalDateTime effectiveAt,
        LocalDateTime expireAt,
        String remark,
        LocalDateTime createdAt
) {

    public static GrowthLogView from(MemberGrowthLog log) {
        return new GrowthLogView(
                log.getId(),
                log.getSource(),
                log.getBizId(),
                log.getGrowth(),
                log.getBeforeValue(),
                log.getAfterValue(),
                log.getEffectiveAt(),
                log.getExpireAt(),
                log.getRemark(),
                log.getCreatedAt());
    }
}
