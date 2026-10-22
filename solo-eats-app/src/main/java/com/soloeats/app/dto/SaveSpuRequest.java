package com.soloeats.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveSpuRequest(
        Long categoryId,
        @NotBlank(message = "spuName 不能为空")
        @Size(max = 128, message = "spuName 长度不能超过 128")
        String spuName,
        @Size(max = 128, message = "subtitle 长度不能超过 128")
        String subtitle,
        @Size(max = 500, message = "description 长度不能超过 500")
        String description,
        String detailHtml,
        @Size(max = 512, message = "mainImage 长度不能超过 512")
        String mainImage,
        List<String> images,
        List<String> tags,
        Long packagingFee,
        Integer minPurchaseQty,
        Integer limitPerOrder,
        Integer status,
        Integer sortWeight
) {
}
