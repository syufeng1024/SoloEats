package com.soloeats.marketing.service.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 满减/折扣活动新建/编辑入参。
 *
 * <p>status 不在本入参内，新建固定为 0 草稿，启停走
 * {@code PromotionAdminService#changeStatus}。used_budget 由核销链路维护，
 * 后台不可写。
 *
 * @param promotionType 1满减 2折扣，必填
 * @param bearer        1平台 2商家，空则取 1
 * @param scopeType     1全平台 2指定商家 3指定门店 4指定品类 5指定商品，空则取 1
 * @param rules         档位，非空；满减要求每档 discountAmount&gt;0，折扣要求 discountRate∈(0,1)
 * @param scopeRefs     范围项；scopeType=4 要求 refType=3，scopeType=5 要求 refType=4/5
 */
public record PromotionSaveCommand(
        Integer promotionType,
        String promotionName,
        Integer bearer,
        Long merchantId,
        Long shopId,
        Integer scopeType,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Integer stackable,
        Integer priority,
        Long totalBudget,
        List<PromotionRuleCommand> rules,
        List<PromotionScopeRefCommand> scopeRefs
) {

    /**
     * 档位入参。阈值为分；满减用 discountAmount，折扣用 discountRate + maxDiscount，
     * 两者按 promotionType 互斥，不匹配的字段在落库时置空。
     */
    public record PromotionRuleCommand(
            Long thresholdAmount,
            Long discountAmount,
            BigDecimal discountRate,
            Long maxDiscount,
            Integer sort
    ) {
    }

    /**
     * 范围项入参。refType 取值沿用 t_promotion_scope：1商家 2门店 3品类 4SPU 5SKU。
     */
    public record PromotionScopeRefCommand(
            Integer refType,
            Long refId
    ) {
    }
}
