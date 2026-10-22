package com.soloeats.product.service.command;

import java.util.List;

/**
 * 提交评价。评分 1~5，匿名由 anonymous=1 表示。
 */
public record CreateReviewCommand(
        Long orderId,
        Long orderItemId,
        Integer overallScore,
        Integer tasteScore,
        Integer packageScore,
        Integer deliveryScore,
        String content,
        List<String> images,
        List<String> tags,
        Integer anonymous
) {
}
