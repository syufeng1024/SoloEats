package com.soloeats.product.service.command;

/**
 * 字段为 null 表示不修改；specJson 传空字符串表示清空规格。
 */
public record UpdateSkuCommand(
        String skuName,
        String specJson,
        String imageUrl,
        Long price,
        Integer status
) {
}
