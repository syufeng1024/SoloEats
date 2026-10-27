package com.soloeats.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * 埋点批量上报。条数上限 20 由 TrackService 校验并返回 10001。
 */
public record TrackBatchRequest(
        @NotEmpty List<@Valid TrackEventItem> events
) {

    public record TrackEventItem(
            @NotBlank String eventType,
            String deviceId,
            String bizType,
            Long bizId,
            String source,
            Map<String, Object> extra
    ) {
    }
}
