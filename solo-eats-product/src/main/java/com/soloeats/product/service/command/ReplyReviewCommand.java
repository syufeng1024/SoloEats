package com.soloeats.product.service.command;

/**
 * 商家回复，最长 500 字，仅一次。
 */
public record ReplyReviewCommand(
        String reply
) {
}
