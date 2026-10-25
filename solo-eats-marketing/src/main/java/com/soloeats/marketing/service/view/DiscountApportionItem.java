package com.soloeats.marketing.service.view;

/**
 * 优惠分摊明细。payableAmount 为该项折后金额（分），discountAmount 为分摊到该项的优惠（分）。
 */
public record DiscountApportionItem(
        Long skuId,
        long payableAmount,
        long discountAmount
) {
}
