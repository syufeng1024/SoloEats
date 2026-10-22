package com.soloeats.product.service.view;

/**
 * 估清原因统计。
 */
public record ReasonStatView(
        String reason,
        long count
) {
}
