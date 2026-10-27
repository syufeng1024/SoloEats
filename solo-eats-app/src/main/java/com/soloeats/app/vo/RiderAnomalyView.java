package com.soloeats.app.vo;

import java.time.LocalDateTime;

/**
 * 骑手本人上报的配送异常，来源 t_rider_ping_anomaly。
 * imagesJson 原样返回 JSON 数组字符串，前端不解析时不展示即可。
 */
public record RiderAnomalyView(
        Long id,
        Long deliveryId,
        Integer type,
        String typeName,
        String remark,
        String imagesJson,
        LocalDateTime createdAt
) {
}
