package com.soloeats.store.service.command;

import java.math.BigDecimal;
import java.util.List;

/**
 * 配送围栏。圆形必须给 centerLng/centerLat/radiusM；
 * 多边形必须给 3~50 个点，由服务层自动闭合首尾。
 */
public record SaveDeliveryAreaCommand(
        Integer areaType,
        BigDecimal centerLng,
        BigDecimal centerLat,
        Integer radiusM,
        List<Point> polygon
) {

    public record Point(BigDecimal lng, BigDecimal lat) {
    }
}
