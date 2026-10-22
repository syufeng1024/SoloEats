package com.soloeats.product.service.command;

import java.util.List;

/**
 * 追评入参。每条评价只允许一次追评。
 */
public record AppendReviewCommand(
        String content,
        List<String> images
) {
}
