package com.soloeats.app.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 营业时段全量替换。endTime 早于 startTime 时按跨天处理（如 22:00-02:00）。
 */
public record SaveBusinessHourRequest(
        @NotEmpty @Size(max = 50) List<@Valid Hour> hours
) {

    public record Hour(
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotBlank String startTime,
            @NotBlank String endTime
    ) {
    }
}
