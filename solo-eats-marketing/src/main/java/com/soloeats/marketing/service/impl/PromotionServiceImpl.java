package com.soloeats.marketing.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.Promotion;
import com.soloeats.marketing.domain.entity.PromotionRule;
import com.soloeats.marketing.mapper.PromotionMapper;
import com.soloeats.marketing.mapper.PromotionRuleMapper;
import com.soloeats.marketing.service.DiscountApportioner;
import com.soloeats.marketing.service.MarketingCalculateCommand;
import com.soloeats.marketing.service.PromotionService;
import com.soloeats.marketing.service.view.DiscountApportionItem;
import com.soloeats.marketing.service.view.MarketingCalculateResult;
import com.soloeats.marketing.service.view.PromotionHitRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * P0 版满减试算：平台级（bearer=1）、全平台（scopeType=1）、单档满减。
 * 阶梯/折扣/商家满减/秒杀在阶段A 补齐，本类不预埋。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private static final int TYPE_FULL_REDUCTION = 1;
    private static final int BEARER_PLATFORM = 1;
    private static final int SCOPE_ALL_PLATFORM = 1;
    private static final int STATUS_ACTIVE = 1;
    private static final int STACKABLE_YES = 1;
    private static final long MIN_PAYABLE = 1L;

    private final PromotionMapper promotionMapper;
    private final PromotionRuleMapper promotionRuleMapper;

    @Override
    public MarketingCalculateResult calculate(MarketingCalculateCommand command) {
        validate(command);

        long goodsAmount = command.goodsAmount();
        long couponDiscount = command.couponDiscount() == null ? 0L : command.couponDiscount();
        LocalDateTime at = command.at() == null ? LocalDateTime.now() : command.at();
        List<MarketingCalculateCommand.MarketingItem> items =
                command.items() == null ? List.of() : command.items();

        // 分摊基数：有明细时不能超过明细合计，保证单项分摊不超过该行金额。
        long itemTotal = items.stream()
                .mapToLong(MarketingCalculateCommand.MarketingItem::payableAmount)
                .sum();
        long discountCap = items.isEmpty() ? goodsAmount : Math.min(goodsAmount, itemTotal);

        Promotion hitPromotion = null;
        PromotionRule hitRule = null;
        long discountAmount = 0L;

        List<Promotion> candidates = promotionMapper.selectActivePlatformPromotions(at);
        if (candidates == null) {
            candidates = List.of();
        }
        for (Promotion promotion : candidates) {
            if (!isAvailable(promotion, at)) {
                continue;
            }
            // 9.8：券与满减默认互斥，只有 stackable=1 才允许同单叠加。
            if (couponDiscount > 0 && !Objects.equals(promotion.getStackable(), STACKABLE_YES)) {
                continue;
            }
            PromotionRule rule = promotionRuleMapper.selectBestRule(
                    promotion.getId(), goodsAmount);
            if (rule == null) {
                continue;
            }
            long threshold = rule.getThresholdAmount() == null ? 0L : rule.getThresholdAmount();
            if (threshold > goodsAmount) {
                continue;
            }
            long ruleDiscount = rule.getDiscountAmount() == null ? 0L : rule.getDiscountAmount();
            if (ruleDiscount <= 0L) {
                continue;
            }
            long applicable = Math.min(ruleDiscount, discountCap);
            if (applicable <= 0L) {
                continue;
            }
            hitPromotion = promotion;
            hitRule = rule;
            discountAmount = applicable;
            break;
        }

        List<DiscountApportionItem> apportion = List.of();
        if (hitPromotion != null && !items.isEmpty()) {
            List<DiscountApportionItem> inputs = new ArrayList<>(items.size());
            for (MarketingCalculateCommand.MarketingItem item : items) {
                inputs.add(new DiscountApportionItem(item.skuId(), item.payableAmount(), 0L));
            }
            apportion = DiscountApportioner.apportion(inputs, discountAmount);
        }

        long payableAmount = Math.max(goodsAmount - couponDiscount - discountAmount, MIN_PAYABLE);

        if (hitPromotion == null) {
            log.debug("marketing calculate no hit goodsAmount={} couponDiscount={} at={}",
                    goodsAmount, couponDiscount, at);
            return new MarketingCalculateResult(
                    null, null, 0L, payableAmount, List.of(), List.of());
        }

        log.debug("marketing calculate hit promotionId={} discount={} payable={}",
                hitPromotion.getId(), discountAmount, payableAmount);
        return new MarketingCalculateResult(
                hitPromotion.getId(),
                hitPromotion.getPromotionName(),
                discountAmount,
                payableAmount,
                apportion,
                List.of(PromotionHitRule.from(hitRule)));
    }

    /**
     * SQL 已按同样条件过滤，这里再判一次：试算被直接调用时也不受调用方数据源影响，
     * 单测可覆盖未开始/已结束分支。
     */
    private boolean isAvailable(Promotion promotion, LocalDateTime at) {
        if (promotion == null || promotion.getId() == null) {
            return false;
        }
        if (!Objects.equals(promotion.getPromotionType(), TYPE_FULL_REDUCTION)
                || !Objects.equals(promotion.getBearer(), BEARER_PLATFORM)
                || !Objects.equals(promotion.getScopeType(), SCOPE_ALL_PLATFORM)
                || !Objects.equals(promotion.getStatus(), STATUS_ACTIVE)
                || Objects.equals(promotion.getDeleted(), 1)) {
            return false;
        }
        return promotion.getStartAt() != null
                && promotion.getEndAt() != null
                && !at.isBefore(promotion.getStartAt())
                && !at.isAfter(promotion.getEndAt());
    }

    private void validate(MarketingCalculateCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "试算参数不能为空");
        }
        if (command.goodsAmount() == null || command.goodsAmount() < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "goodsAmount 不能为空或负数");
        }
        if (command.couponDiscount() != null && command.couponDiscount() < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "couponDiscount 不能为负数");
        }
        if (command.items() != null) {
            for (MarketingCalculateCommand.MarketingItem item : command.items()) {
                if (item == null || item.payableAmount() == null || item.payableAmount() < 0L) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "明细 payableAmount 不能为空或负数");
                }
                if (item.quantity() != null && item.quantity() < 1) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "明细 quantity 必须大于 0");
                }
            }
        }
    }
}
