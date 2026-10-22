package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 配送围栏。圆形传 centerLng/centerLat/radiusM；
 * 多边形传 3~50 个 polygon 点，首尾自动闭合。
 */
public record SaveDeliveryAreaRequest(
        @NotNull @Min(1) @Max(2) Integer areaType,
        BigDecimal centerLng,
        BigDecimal centerLat,
        @Min(100) @Max(50000) Integer radiusM,
        @Size(max = 50) List<Point> polygon
) {

    public record Point(
            @NotNull BigDecimal lng,
            @NotNull BigDecimal lat
    ) {
    }
}
