package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateReviewRequest(
        @NotNull(message = "orderId 不能为空")
        Long orderId,
        @NotNull(message = "orderItemId 不能为空")
        Long orderItemId,
        @NotNull(message = "overallScore 不能为空")
        @Min(value = 1, message = "overallScore 最小为 1")
        @Max(value = 5, message = "overallScore 最大为 5")
        Integer overallScore,
        @Min(value = 1, message = "tasteScore 最小为 1")
        @Max(value = 5, message = "tasteScore 最大为 5")
        Integer tasteScore,
        @Min(value = 1, message = "packageScore 最小为 1")
        @Max(value = 5, message = "packageScore 最大为 5")
        Integer packageScore,
        @Min(value = 1, message = "deliveryScore 最小为 1")
        @Max(value = 5, message = "deliveryScore 最大为 5")
        Integer deliveryScore,
        @Size(max = 500, message = "content 长度不能超过 500")
        String content,
        List<String> images,
        List<String> tags,
        Integer anonymous
) {
}
