package com.soloeats.app.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RestoreSoldOutRequest(
        @NotNull(message = "shopId 不能为空")
        Long shopId,
        @NotEmpty(message = "skuIds 不能为空")
        List<Long> skuIds
) {
}
