package com.soloeats.product.service.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价视图。匿名评价不返回 userId。
 */
public record ReviewView(
        Long id,
        Long orderId,
        Long shopId,
        Long spuId,
        Long skuId,
        Long userId,
        Integer overallScore,
        Integer tasteScore,
        Integer packageScore,
        Integer deliveryScore,
        String content,
        List<String> images,
        List<String> tags,
        Integer anonymous,
        Integer status,
        Integer topFlag,
        String merchantReply,
        LocalDateTime replyAt,
        String appendContent,
        LocalDateTime appendAt,
        LocalDateTime createdAt
) {
}
