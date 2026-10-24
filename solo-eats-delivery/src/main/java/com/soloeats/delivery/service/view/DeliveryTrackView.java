package com.soloeats.delivery.service.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端轨迹：最近 100 个轨迹点 + 骑手 Redis 实时位置。
 * riderPosition 只暴露坐标与心跳时间，不返回骑手手机号/姓名。
 */
public record DeliveryTrackView(
        List<TrackPointView> points,
        RiderPosition riderPosition
) {

    public record RiderPosition(
            BigDecimal lng,
            BigDecimal lat,
            LocalDateTime lastPing
    ) {
    }
}
