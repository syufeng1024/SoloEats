package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSkuRequest(
        @NotNull(message = "spuId 不能为空")
        @Min(value = 1, message = "spuId 必须是正整数")
        Long spuId,

        @NotBlank(message = "skuName 不能为空")
        @Size(max = 128, message = "skuName 长度不能超过 128")
        String skuName,

        String specJson,

        @Size(max = 512, message = "imageUrl 长度不能超过 512")
        String imageUrl,

        @NotNull(message = "price 不能为空")
        @Min(value = 1, message = "price 必须是正整数（单位分）")
        Long price,

        @Min(value = 0, message = "status 只能是 0 或 1")
        @Max(value = 1, message = "status 只能是 0 或 1")
        Integer status,

        @Min(value = 0, message = "initialStock 不能为负数")
        Integer initialStock
) {
}
