package com.soloeats.product.service.command;

/**
 * 字段为 null 表示不修改；至少提供一个字段。
 */
public record UpdateSkuStockCommand(
        Integer availableQty,
        Boolean soldOut
) {
}
