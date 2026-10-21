package com.soloeats.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateSkuRequest(
        @Size(max = 128, message = "skuName 长度不能超过 128")
        String skuName,

        String specJson,

        @Size(max = 512, message = "imageUrl 长度不能超过 512")
        String imageUrl,

        @Min(value = 1, message = "price 必须是正整数（单位分）")
        Long price,

        @Min(value = 0, message = "status 只能是 0 或 1")
        @Max(value = 1, message = "status 只能是 0 或 1")
        Integer status
) {
}
