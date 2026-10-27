package com.soloeats.marketing.service.view;

import com.soloeats.marketing.domain.entity.PointLog;

import java.time.LocalDateTime;

/**
 * 积分明细。point 可负（兑换、过期）。
 */
public record PointLogView(
        Long id,
        Integer source,
        Long bizId,
        Integer point,
        Integer beforeBalance,
        Integer afterBalance,
        LocalDateTime expireAt,
        String remark,
        LocalDateTime createdAt
) {

    public static PointLogView from(PointLog log) {
        return new PointLogView(
                log.getId(),
                log.getSource(),
                log.getBizId(),
                log.getPoint(),
                log.getBeforeBalance(),
                log.getAfterBalance(),
                log.getExpireAt(),
                log.getRemark(),
                log.getCreatedAt());
    }
}
