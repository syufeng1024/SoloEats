package com.soloeats.product.service.command;

public record CreateSkuCommand(
        Long spuId,
        String skuName,
        String specJson,
        String imageUrl,
        Long price,
        Integer status,
        Integer initialStock
) {
}
