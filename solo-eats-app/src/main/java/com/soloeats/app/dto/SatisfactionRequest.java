package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SatisfactionRequest(
        @NotNull(message = "satisfaction 不能为空")
        @Min(value = 1, message = "satisfaction 只能是 1~5")
        @Max(value = 5, message = "satisfaction 只能是 1~5")
        Integer satisfaction,
        @Size(max = 500, message = "satisfactionRemark 最长 500 个字符")
        String satisfactionRemark
) {
}
