package com.soloeats.delivery.service.view;

import java.math.BigDecimal;

/**
 * 派单评分输入。distanceM 为骑手当前位置到取货点的直线距离；
 * remainingSeconds 为当前派单窗口剩余秒数，越大表示越需要优先推送。
 */
public record DispatchCandidate(
        Long riderId,
        double distanceM,
        int currentLoad,
        BigDecimal rating,
        long remainingSeconds
) {
}
