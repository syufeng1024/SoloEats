package com.soloeats.app.dto;

import com.soloeats.delivery.service.command.TrackPointCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 骑手批量上报轨迹。单次最多 20 点，服务层再做一次上限校验。
 */
public record ReportTrackRequest(
        @NotNull(message = "deliveryId 不能为空")
        Long deliveryId,

        @NotEmpty(message = "轨迹点不能为空")
        @Size(max = 20, message = "一次最多上报 20 个轨迹点")
        @Valid
        List<TrackPoint> points
) {

    public record TrackPoint(
            @NotNull(message = "lng 不能为空")
            BigDecimal lng,
            @NotNull(message = "lat 不能为空")
            BigDecimal lat,
            BigDecimal speed,
            BigDecimal accuracy,
            LocalDateTime reportAt
    ) {
    }

    public List<TrackPointCommand> toCommands() {
        return points.stream()
                .map(point -> new TrackPointCommand(
                        deliveryId, point.lng(), point.lat(),
                        point.speed(), point.accuracy(), point.reportAt()))
                .toList();
    }
}
