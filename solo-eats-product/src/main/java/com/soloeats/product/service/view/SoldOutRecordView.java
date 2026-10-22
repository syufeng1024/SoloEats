package com.soloeats.product.service.view;

import java.time.LocalDateTime;

/**
 * 估清记录视图。
 */
public record SoldOutRecordView(
        Long id,
        Long shopId,
        Long skuId,
        Long spuId,
        String reason,
        LocalDateTime soldOutAt,
        LocalDateTime restoredAt,
        Long operatorId,
        String operatorName
) {
}
