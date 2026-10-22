package com.soloeats.app.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SoldOutRequest(
        @NotNull(message = "shopId 不能为空")
        Long shopId,
        @NotEmpty(message = "skuIds 不能为空")
        List<Long> skuIds,
        @Size(max = 128, message = "reason 长度不能超过 128")
        String reason
) {
}
