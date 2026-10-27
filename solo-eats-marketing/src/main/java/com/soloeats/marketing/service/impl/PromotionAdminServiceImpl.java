package com.soloeats.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.marketing.domain.entity.Promotion;
import com.soloeats.marketing.domain.entity.PromotionRule;
import com.soloeats.marketing.domain.entity.PromotionScope;
import com.soloeats.marketing.mapper.PromotionMapper;
import com.soloeats.marketing.mapper.PromotionRuleMapper;
import com.soloeats.marketing.mapper.PromotionScopeMapper;
import com.soloeats.marketing.service.PromotionAdminService;
import com.soloeats.marketing.service.command.PromotionSaveCommand;
import com.soloeats.marketing.service.view.PromotionView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 后台满减/折扣活动管理。
 *
 * <p>子表策略：t_promotion_rule / t_promotion_scope 都是纯子表（无 deleted 列），
 * 编辑时按 promotion_id 物理删除后整批重插，避免残留已下线档位。
 *
 * <p>编辑只覆盖主表可编辑字段，status 由 {@code changeStatus} 单独维护，
 * used_budget 由核销链路维护，两者都不从本入口写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionAdminServiceImpl implements PromotionAdminService {

    private static final int TYPE_FULL_REDUCTION = 1;
    private static final int TYPE_DISCOUNT = 2;
    private static final int BEARER_PLATFORM = 1;
    private static final int BEARER_MERCHANT = 2;
    private static final int SCOPE_ALL_PLATFORM = 1;
    private static final int SCOPE_MERCHANT = 2;
    private static final int SCOPE_SHOP = 3;
    private static final int SCOPE_CATEGORY = 4;
    private static final int SCOPE_GOODS = 5;
    private static final int REF_TYPE_CATEGORY = 3;
    private static final int REF_TYPE_SPU = 4;
    private static final int REF_TYPE_SKU = 5;
    private static final int STATUS_DRAFT = 0;
    private static final int STACKABLE_NO = 0;
    /** t_promotion_rule.discount_rate 是 decimal(4,2)，按列精度落库，避免 Java 侧 4 位小数被 DB 静默截断。 */
    private static final int RATE_SCALE = 2;
    private static final int NAME_MAX_LENGTH = 64;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final Set<Integer> PROMOTION_TYPES = Set.of(TYPE_FULL_REDUCTION, TYPE_DISCOUNT);
    private static final Set<Integer> BEARERS = Set.of(BEARER_PLATFORM, BEARER_MERCHANT);
    private static final Set<Integer> SCOPE_TYPES = Set.of(
            SCOPE_ALL_PLATFORM, SCOPE_MERCHANT, SCOPE_SHOP, SCOPE_CATEGORY, SCOPE_GOODS);
    private static final Set<Integer> STATUSES = Set.of(0, 1, 2, 3);
    private static final Set<Integer> REF_TYPES = Set.of(1, 2, 3, 4, 5);

    private final PromotionMapper promotionMapper;
    private final PromotionRuleMapper promotionRuleMapper;
    private final PromotionScopeMapper promotionScopeMapper;

    @Override
    public PageResult<PromotionView> page(Integer status, Integer promotionType, Integer bearer,
                                          String keyword, long pageNum, long pageSize) {
        if (status != null && !STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "status 只能是 0草稿 / 1进行中 / 2已结束 / 3暂停");
        }
        if (promotionType != null && !PROMOTION_TYPES.contains(promotionType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "promotionType 只能是 1 满减 / 2 折扣");
        }
        if (bearer != null && !BEARERS.contains(bearer)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bearer 只能是 1 平台 / 2 商家");
        }

        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);

        LambdaQueryWrapper<Promotion> wrapper = new LambdaQueryWrapper<Promotion>()
                .eq(Promotion::getDeleted, 0)
                .eq(status != null, Promotion::getStatus, status)
                .eq(promotionType != null, Promotion::getPromotionType, promotionType)
                .eq(bearer != null, Promotion::getBearer, bearer)
                .like(StringUtils.hasText(keyword), Promotion::getPromotionName, trimToNull(keyword))
                .orderByDesc(Promotion::getPriority)
                .orderByDesc(Promotion::getId);

        Page<Promotion> page = promotionMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        // 列表不展开档位与范围项：最多 100 行，逐行查子表会放大成 200 次查询。
        List<PromotionView> views = page.getRecords().stream()
                .map(promotion -> PromotionView.from(promotion, List.of(), List.of()))
                .toList();
        return PageResult.of(views, page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public PromotionView detail(Long id) {
        Promotion promotion = requirePromotion(id);
        return PromotionView.from(
                promotion,
                promotionRuleMapper.selectByPromotionId(id),
                promotionScopeMapper.selectByPromotionId(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PromotionSaveCommand command) {
        Normalized normalized = normalize(command);

        Promotion promotion = new Promotion();
        applyMainFields(promotion, normalized);
        promotion.setStatus(STATUS_DRAFT);
        promotion.setUsedBudget(0L);
        promotion.setDeleted(0);
        promotionMapper.insert(promotion);

        Long promotionId = promotion.getId();
        if (promotionId == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "活动创建失败，未生成主键");
        }
        insertChildren(promotionId, normalized);

        log.info("promotion created promotionId={} type={} scopeType={} rules={} scopes={}",
                promotionId, normalized.promotionType(), normalized.scopeType(),
                normalized.rules().size(), normalized.scopes().size());
        return promotionId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PromotionSaveCommand command) {
        requirePromotion(id);
        Normalized normalized = normalize(command);

        LambdaUpdateWrapper<Promotion> wrapper = new LambdaUpdateWrapper<Promotion>()
                .eq(Promotion::getId, id)
                .eq(Promotion::getDeleted, 0)
                .set(Promotion::getPromotionType, normalized.promotionType())
                .set(Promotion::getPromotionName, normalized.promotionName())
                .set(Promotion::getBearer, normalized.bearer())
                .set(Promotion::getMerchantId, normalized.merchantId())
                .set(Promotion::getShopId, normalized.shopId())
                .set(Promotion::getScopeType, normalized.scopeType())
                .set(Promotion::getStartAt, normalized.startAt())
                .set(Promotion::getEndAt, normalized.endAt())
                .set(Promotion::getStackable, normalized.stackable())
                .set(Promotion::getPriority, normalized.priority())
                .set(Promotion::getTotalBudget, normalized.totalBudget());
        if (promotionMapper.update(null, wrapper) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "活动不存在或已删除");
        }

        promotionRuleMapper.deleteByPromotionId(id);
        promotionScopeMapper.deleteByPromotionId(id);
        insertChildren(id, normalized);

        log.info("promotion updated promotionId={} type={} scopeType={} rules={} scopes={}",
                id, normalized.promotionType(), normalized.scopeType(),
                normalized.rules().size(), normalized.scopes().size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || !STATUSES.contains(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "status 只能是 0草稿 / 1进行中 / 2已结束 / 3暂停");
        }
        requirePromotion(id);

        LambdaUpdateWrapper<Promotion> wrapper = new LambdaUpdateWrapper<Promotion>()
                .eq(Promotion::getId, id)
                .eq(Promotion::getDeleted, 0)
                .set(Promotion::getStatus, status);
        if (promotionMapper.update(null, wrapper) == 0) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "活动不存在或已删除");
        }
        log.info("promotion status changed promotionId={} status={}", id, status);
    }

    // ---------------------------------------------------------------
    // 校验与归一
    // ---------------------------------------------------------------

    private Normalized normalize(PromotionSaveCommand command) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请求参数不能为空");
        }
        int promotionType = requirePromotionType(command.promotionType());
        String promotionName = normalizeName(command.promotionName());
        int bearer = normalizeBearer(command.bearer());
        int scopeType = requireScopeType(command.scopeType());
        validateSchedule(command.startAt(), command.endAt());
        int stackable = normalizeStackable(command.stackable());
        int priority = command.priority() == null ? 0 : command.priority();
        Long totalBudget = normalizeTotalBudget(command.totalBudget());

        Long merchantId = normalizePositiveId(command.merchantId(), "merchantId");
        Long shopId = normalizePositiveId(command.shopId(), "shopId");
        if (scopeType == SCOPE_MERCHANT && merchantId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "scopeType=2 必须配置 merchantId");
        }
        if (scopeType == SCOPE_SHOP && shopId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "scopeType=3 必须配置 shopId");
        }

        List<PromotionRule> rules = normalizeRules(promotionType, command.rules());
        List<PromotionScope> scopes = normalizeScopes(scopeType, command.scopeRefs());

        return new Normalized(promotionType, promotionName, bearer, merchantId, shopId, scopeType,
                command.startAt(), command.endAt(), stackable, priority, totalBudget, rules, scopes);
    }

    private int requirePromotionType(Integer promotionType) {
        if (promotionType == null || !PROMOTION_TYPES.contains(promotionType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "promotionType 只能是 1 满减 / 2 折扣");
        }
        return promotionType;
    }

    private String normalizeName(String promotionName) {
        String trimmed = trimToNull(promotionName);
        if (trimmed == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "promotionName 不能为空");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "promotionName 不能超过 " + NAME_MAX_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private int normalizeBearer(Integer bearer) {
        if (bearer == null) {
            return BEARER_PLATFORM;
        }
        if (!BEARERS.contains(bearer)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bearer 只能是 1 平台 / 2 商家");
        }
        return bearer;
    }

    private int requireScopeType(Integer scopeType) {
        if (scopeType == null) {
            return SCOPE_ALL_PLATFORM;
        }
        if (!SCOPE_TYPES.contains(scopeType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "scopeType 只能是 1~5");
        }
        return scopeType;
    }

    private void validateSchedule(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "startAt 与 endAt 不能为空");
        }
        if (!startAt.isBefore(endAt)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "endAt 必须晚于 startAt");
        }
    }

    private int normalizeStackable(Integer stackable) {
        if (stackable == null) {
            return STACKABLE_NO;
        }
        if (!Objects.equals(stackable, 0) && !Objects.equals(stackable, 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "stackable 只能是 0 不可叠加 / 1 可叠加");
        }
        return stackable;
    }

    private Long normalizeTotalBudget(Long totalBudget) {
        if (totalBudget != null && totalBudget < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "totalBudget 不能为负数");
        }
        return totalBudget;
    }

    private Long normalizePositiveId(Long id, String field) {
        if (id == null) {
            return null;
        }
        if (id <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, field + " 必须是正整数");
        }
        return id;
    }

    /**
     * 档位归一：满减与折扣互斥的字段按类型置空，防止一行里同时存在
     * discount_amount 与 discount_rate 造成命中口径歧义。
     */
    private List<PromotionRule> normalizeRules(int promotionType,
                                               List<PromotionSaveCommand.PromotionRuleCommand> commands) {
        if (commands == null || commands.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "rules 不能为空");
        }
        List<PromotionRule> rules = new ArrayList<>(commands.size());
        int index = 0;
        for (PromotionSaveCommand.PromotionRuleCommand command : commands) {
            if (command == null) {
                throw new BizException(ErrorCode.PARAM_INVALID, "rules 不能包含空档位");
            }
            long thresholdAmount = command.thresholdAmount() == null ? 0L : command.thresholdAmount();
            if (thresholdAmount < 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID, "thresholdAmount 不能为负数");
            }

            PromotionRule rule = new PromotionRule();
            rule.setThresholdAmount(thresholdAmount);
            rule.setSort(command.sort() == null ? index : command.sort());

            if (promotionType == TYPE_FULL_REDUCTION) {
                long discountAmount = command.discountAmount() == null ? 0L : command.discountAmount();
                if (discountAmount <= 0L) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "满减活动的每档 discountAmount 必须大于 0");
                }
                rule.setDiscountAmount(discountAmount);
                rule.setDiscountRate(null);
                rule.setMaxDiscount(null);
            } else {
                BigDecimal rate = command.discountRate();
                if (rate == null) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "折扣活动的每档 discountRate 不能为空");
                }
                BigDecimal scaledRate = rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
                if (scaledRate.compareTo(BigDecimal.ZERO) <= 0
                        || scaledRate.compareTo(BigDecimal.ONE) >= 0) {
                    throw new BizException(ErrorCode.PARAM_INVALID,
                            "折扣活动的每档 discountRate 必须在 (0,1) 区间");
                }
                Long maxDiscount = command.maxDiscount();
                if (maxDiscount != null && maxDiscount < 0L) {
                    throw new BizException(ErrorCode.PARAM_INVALID, "maxDiscount 不能为负数");
                }
                rule.setDiscountRate(scaledRate);
                rule.setMaxDiscount(maxDiscount);
                rule.setDiscountAmount(null);
            }

            rules.add(rule);
            index++;
        }
        return List.copyOf(rules);
    }

    /**
     * 范围项归一。scopeType=1/2/3 的范围由主表列表达，传入的 scopeRefs 不落库；
     * scopeType=4 只接受 refType=3（品类），scopeType=5 只接受 refType=4/5（SPU/SKU）。
     * 同一 (refType, refId) 去重，避免撞 uk_promo_ref。
     */
    private List<PromotionScope> normalizeScopes(int scopeType,
                                                 List<PromotionSaveCommand.PromotionScopeRefCommand> refs) {
        if (scopeType != SCOPE_CATEGORY && scopeType != SCOPE_GOODS) {
            return List.of();
        }
        if (refs == null || refs.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "scopeType=4/5 必须配置 scopeRefs");
        }

        LinkedHashMap<String, PromotionScope> distinct = new LinkedHashMap<>();
        for (PromotionSaveCommand.PromotionScopeRefCommand ref : refs) {
            if (ref == null || ref.refType() == null || !REF_TYPES.contains(ref.refType())
                    || ref.refId() == null || ref.refId() <= 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "scopeRefs 的 refType 只能是 1~5，refId 必须是正整数");
            }
            if (scopeType == SCOPE_CATEGORY && ref.refType() != REF_TYPE_CATEGORY) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "scopeType=4 的 refType 必须是 3（品类）");
            }
            if (scopeType == SCOPE_GOODS
                    && ref.refType() != REF_TYPE_SPU && ref.refType() != REF_TYPE_SKU) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        "scopeType=5 的 refType 必须是 4（SPU）或 5（SKU）");
            }

            PromotionScope scope = new PromotionScope();
            scope.setRefType(ref.refType());
            scope.setRefId(ref.refId());
            distinct.putIfAbsent(ref.refType() + ":" + ref.refId(), scope);
        }
        return List.copyOf(distinct.values());
    }

    // ---------------------------------------------------------------
    // 落库
    // ---------------------------------------------------------------

    private void applyMainFields(Promotion promotion, Normalized normalized) {
        promotion.setPromotionType(normalized.promotionType());
        promotion.setPromotionName(normalized.promotionName());
        promotion.setBearer(normalized.bearer());
        promotion.setMerchantId(normalized.merchantId());
        promotion.setShopId(normalized.shopId());
        promotion.setScopeType(normalized.scopeType());
        promotion.setStartAt(normalized.startAt());
        promotion.setEndAt(normalized.endAt());
        promotion.setStackable(normalized.stackable());
        promotion.setPriority(normalized.priority());
        promotion.setTotalBudget(normalized.totalBudget());
    }

    private void insertChildren(Long promotionId, Normalized normalized) {
        for (PromotionRule rule : normalized.rules()) {
            rule.setPromotionId(promotionId);
            promotionRuleMapper.insert(rule);
        }
        for (PromotionScope scope : normalized.scopes()) {
            scope.setPromotionId(promotionId);
            promotionScopeMapper.insert(scope);
        }
    }

    private Promotion requirePromotion(Long id) {
        Promotion promotion = id == null ? null : promotionMapper.selectById(id);
        if (promotion == null || Objects.equals(promotion.getDeleted(), 1)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "活动不存在");
        }
        return promotion;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static long normalizePageNum(long pageNum) {
        return pageNum < 1L ? 1L : pageNum;
    }

    private static long normalizePageSize(long pageSize) {
        return pageSize < 1L ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private record Normalized(
            int promotionType,
            String promotionName,
            int bearer,
            Long merchantId,
            Long shopId,
            int scopeType,
            LocalDateTime startAt,
            LocalDateTime endAt,
            int stackable,
            int priority,
            Long totalBudget,
            List<PromotionRule> rules,
            List<PromotionScope> scopes
    ) {
    }
}
