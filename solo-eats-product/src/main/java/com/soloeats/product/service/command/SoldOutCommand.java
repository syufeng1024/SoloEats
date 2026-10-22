package com.soloeats.product.service.command;

import java.util.List;

/**
 * 批量估清/恢复入参。
 */
public record SoldOutCommand(
        Long shopId,
        List<Long> skuIds,
        String reason
) {
}
