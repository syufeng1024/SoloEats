package com.soloeats.marketing.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.Promotion;
import com.soloeats.marketing.domain.entity.PromotionRule;
import com.soloeats.marketing.mapper.PromotionMapper;
import com.soloeats.marketing.mapper.PromotionRuleMapper;
import com.soloeats.marketing.mapper.PromotionScopeMapper;
import com.soloeats.marketing.service.DiscountApportioner;
import com.soloeats.marketing.service.MarketingCalculateCommand;
import com.soloeats.marketing.service.PromotionService;
import com.soloeats.marketing.service.view.DiscountApportionItem;
import com.soloeats.marketing.service.view.MarketingCalculateResult;
import com.soloeats.marketing.service.view.PromotionAvailableView;
import com.soloeats.marketing.service.view.PromotionHitRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 满减/折扣试算引擎（阶段A）。
 *
 * <p>命中流程：候选活动（status=1、deleted=0、时间区间内，priority DESC）逐条判定
 * 类型 → 时间 → 范围 → 券叠加开关 → 档位金额，取第一个算出正优惠的活动即停。
 * 同一活动内由 {@code selectBestRule} 取达标的最大门槛档。
 *
 * <p>金额单位为分；折扣率用 BigDecimal + HALF_UP，全链路不出现 double/float。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private static final int TYPE_FULL_REDUCTION = 1;
    private static final int TYPE_DISCOUNT = 2;
    private static final int SCOPE_ALL_PLATFORM = 1;
    private static final int SCOPE_MERCHANT = 2;
    private static final int SCOPE_SHOP = 3;
    private static final int SCOPE_CATEGORY = 4;
    private static final int SCOPE_GOODS = 5;
    private static final int STATUS_ACTIVE = 1;
    private static final int STACKABLE_YES = 1;
    private static final long MIN_PAYABLE = 1L;

    private final PromotionMapper promotionMapper;
    private final PromotionRuleMapper promotionRuleMapper;
    private final PromotionScopeMapper promotionScopeMapper;

    @Override
    public MarketingCalculateResult calculate(MarketingCalculateCommand command) {
        validate(command);

        long goodsAmount = command.goodsAmount();
        long couponDiscount = command.couponDiscount() == null ? 0L : command.couponDiscount();
        LocalDateTime at = command.at() == null ? LocalDateTime.now() : command.at();
        List<MarketingCalculateCommand.MarketingItem> items =
                command.items() == null ? List.of() : command.items();
        // 商家归属只解析一次：C 端不传 merchantId 时由 shopId 反查，避免在候选循环里逐条查库。
        Long merchantId = resolveMerchantId(command);

        // 分摊基数：有明细时不能超过明细合计，保证单项分摊不超过该行金额。
        long itemTotal = items.stream()
                .mapToLong(MarketingCalculateCommand.MarketingItem::payableAmount)
                .sum();
        long discountCap = items.isEmpty() ? goodsAmount : Math.min(goodsAmount, itemTotal);

        Set<Long> skuIds = collectSkuIds(items);
        List<Promotion> candidates = safeCandidates(at);
        Set<Long> categoryIds = resolveCategoryIdsIfNeeded(candidates, skuIds);

        Promotion hitPromotion = null;
        PromotionRule hitRule = null;
        long discountAmount = 0L;

        for (Promotion promotion : candidates) {
            if (!matchBase(promotion) || !matchTime(promotion, at)) {
                continue;
            }
            if (!matchScope(promotion, merchantId, command.shopId(), skuIds, categoryIds)) {
                continue;
            }
            // 9.8：券与满减默认互斥，只有 stackable=1 才允许同单叠加。
            if (couponDiscount > 0 && !Objects.equals(promotion.getStackable(), STACKABLE_YES)) {
                continue;
            }
            PromotionRule rule = promotionRuleMapper.selectBestRule(
                    promotion.getId(), goodsAmount);
            long applicable = Math.min(
                    resolveDiscount(promotion.getPromotionType(), rule, goodsAmount), discountCap);
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

        log.debug("marketing calculate hit promotionId={} type={} discount={} payable={}",
                hitPromotion.getId(), hitPromotion.getPromotionType(), discountAmount, payableAmount);
        return new MarketingCalculateResult(
                hitPromotion.getId(),
                hitPromotion.getPromotionName(),
                discountAmount,
                payableAmount,
                apportion,
                List.of(PromotionHitRule.from(hitRule)));
    }

    @Override
    public List<PromotionAvailableView> listAvailable(Long shopId, List<Long> spuIds, LocalDateTime at) {
        LocalDateTime effectiveAt = at == null ? LocalDateTime.now() : at;
        List<Long> distinctSpuIds = normalizeSpuIds(spuIds);
        Long merchantId = resolveMerchantIdByShop(shopId);

        Set<Long> skuIds = distinctSpuIds.isEmpty()
                ? Set.of()
                : toLongSet(promotionScopeMapper.selectSkuIdsBySpuIds(distinctSpuIds));
        Set<Long> categoryIds = skuIds.isEmpty()
                ? Set.of()
                : toLongSet(promotionScopeMapper.selectCategoryIdsBySkuIds(List.copyOf(skuIds)));

        List<PromotionAvailableView> views = new ArrayList<>();
        for (Promotion promotion : safeCandidates(effectiveAt)) {
            if (!matchBase(promotion) || !matchTime(promotion, effectiveAt)) {
                continue;
            }
            if (!matchScope(promotion, merchantId, shopId, skuIds, categoryIds)) {
                continue;
            }
            views.add(PromotionAvailableView.from(
                    promotion, promotionRuleMapper.selectByPromotionId(promotion.getId())));
        }
        return List.copyOf(views);
    }

    /**
     * 基础过滤：只参与满减(1)/折扣(2)、进行中、未删除。SQL 已按 status/deleted 过滤，
     * 这里再判一次，试算被直接调用时也不受查询条件影响。
     */
    private boolean matchBase(Promotion promotion) {
        if (promotion == null || promotion.getId() == null) {
            return false;
        }
        Integer promotionType = promotion.getPromotionType();
        boolean supportedType = Objects.equals(promotionType, TYPE_FULL_REDUCTION)
                || Objects.equals(promotionType, TYPE_DISCOUNT);
        return supportedType
                && Objects.equals(promotion.getStatus(), STATUS_ACTIVE)
                && !Objects.equals(promotion.getDeleted(), 1);
    }

    private boolean matchTime(Promotion promotion, LocalDateTime at) {
        return promotion.getStartAt() != null
                && promotion.getEndAt() != null
                && !at.isBefore(promotion.getStartAt())
                && !at.isAfter(promotion.getEndAt());
    }

    /**
     * 范围判定。merchantId/shopId 为 null 表示调用方没有该维度上下文，
     * 对应范围的活动无法判定，按不可用处理；skuIds/categoryIds 为空同理。
     */
    private boolean matchScope(Promotion promotion, Long merchantId, Long shopId,
                               Set<Long> skuIds, Set<Long> categoryIds) {
        Integer scopeType = promotion.getScopeType();
        if (scopeType == null) {
            return false;
        }
        return switch (scopeType) {
            case SCOPE_ALL_PLATFORM -> true;
            case SCOPE_MERCHANT -> merchantId != null
                    && Objects.equals(promotion.getMerchantId(), merchantId);
            case SCOPE_SHOP -> shopId != null && Objects.equals(promotion.getShopId(), shopId);
            case SCOPE_CATEGORY -> matchesCategory(promotion.getId(), categoryIds);
            case SCOPE_GOODS -> matchesSku(promotion.getId(), skuIds);
            default -> false;
        };
    }

    private boolean matchesCategory(Long promotionId, Set<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return false;
        }
        List<Long> scopeCategoryIds = promotionScopeMapper.selectScopeCategoryIds(promotionId);
        return scopeCategoryIds != null && scopeCategoryIds.stream().anyMatch(categoryIds::contains);
    }

    private boolean matchesSku(Long promotionId, Set<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return false;
        }
        List<Long> scopeSkuIds = promotionScopeMapper.selectScopeSkuIds(promotionId);
        return scopeSkuIds != null && scopeSkuIds.stream().anyMatch(skuIds::contains);
    }

    /**
     * 命中档位的优惠金额（分）。满减取档位金额；折扣按 goodsAmount×(1−rate) 四舍五入到分，
     * 再按 maxDiscount 封顶。两种类型都要求 threshold ≤ goodsAmount。
     * 折扣率缺失或不在 (0,1) 区间时该档作废，返回 0。
     */
    private long resolveDiscount(Integer promotionType, PromotionRule rule, long goodsAmount) {
        if (rule == null) {
            return 0L;
        }
        long threshold = rule.getThresholdAmount() == null ? 0L : rule.getThresholdAmount();
        if (threshold > goodsAmount) {
            return 0L;
        }
        if (Objects.equals(promotionType, TYPE_FULL_REDUCTION)) {
            return rule.getDiscountAmount() == null ? 0L : rule.getDiscountAmount();
        }
        if (!Objects.equals(promotionType, TYPE_DISCOUNT)) {
            return 0L;
        }
        BigDecimal rate = rule.getDiscountRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0
                || rate.compareTo(BigDecimal.ONE) >= 0) {
            return 0L;
        }
        long discount = BigDecimal.valueOf(goodsAmount)
                .multiply(BigDecimal.ONE.subtract(rate))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        Long maxDiscount = rule.getMaxDiscount();
        if (maxDiscount != null) {
            discount = Math.min(discount, maxDiscount);
        }
        return discount;
    }

    private List<Promotion> safeCandidates(LocalDateTime at) {
        List<Promotion> candidates = promotionMapper.selectCandidates(at);
        return candidates == null ? List.of() : candidates;
    }

    /**
     * merchantId 优先用调用方传入的；否则用 shopId 反查，保证 C 端不传 merchantId 也能命中商家满减。
     * 反查结果为 null（门店不存在或已删除）时商家范围活动按「无法判定即跳过」处理，不报错。
     */
    private Long resolveMerchantId(MarketingCalculateCommand command) {
        if (command.merchantId() != null) {
            return command.merchantId();
        }
        return resolveMerchantIdByShop(command.shopId());
    }

    private Long resolveMerchantIdByShop(Long shopId) {
        return shopId == null ? null : promotionMapper.selectMerchantIdByShop(shopId);
    }

    /**
     * 只有存在指定品类活动且本次订单有 sku 时才反查品类，避免每次试算都多一次 JOIN。
     */
    private Set<Long> resolveCategoryIdsIfNeeded(List<Promotion> candidates, Set<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return Set.of();
        }
        boolean needsCategory = candidates.stream()
                .anyMatch(promotion -> Objects.equals(promotion.getScopeType(), SCOPE_CATEGORY));
        if (!needsCategory) {
            return Set.of();
        }
        return toLongSet(promotionScopeMapper.selectCategoryIdsBySkuIds(List.copyOf(skuIds)));
    }

    private Set<Long> collectSkuIds(List<MarketingCalculateCommand.MarketingItem> items) {
        Set<Long> skuIds = new LinkedHashSet<>();
        for (MarketingCalculateCommand.MarketingItem item : items) {
            if (item != null && item.skuId() != null && item.skuId() > 0L) {
                skuIds.add(item.skuId());
            }
        }
        return skuIds;
    }

    private List<Long> normalizeSpuIds(List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long spuId : spuIds) {
            if (spuId != null && spuId > 0L) {
                distinct.add(spuId);
            }
        }
        return List.copyOf(distinct);
    }

    private Set<Long> toLongSet(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<Long> result = new HashSet<>(values.size());
        for (Long value : values) {
            if (value != null) {
                result.add(value);
            }
        }
        return result;
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
