package com.soloeats.app.dto;

import com.soloeats.marketing.service.view.DiscountApportionItem;
import com.soloeats.marketing.service.view.MarketingCalculateResult;
import com.soloeats.marketing.service.view.PromotionHitRule;

import java.util.List;

/**
 * 营销试算响应。金额单位分；未命中活动时 promotionId 为 null、discountAmount=0。
 */
public record MarketingCalculateResponse(
        Long promotionId,
        String promotionName,
        long discountAmount,
        long payableAmount,
        List<DiscountApportionItem> apportion,
        List<PromotionHitRule> hitRules
) {

    public static MarketingCalculateResponse from(MarketingCalculateResult result) {
        return new MarketingCalculateResponse(
                result.promotionId(),
                result.promotionName(),
                result.discountAmount(),
                result.payableAmount(),
                result.apportion(),
                result.hitRules());
    }
}
