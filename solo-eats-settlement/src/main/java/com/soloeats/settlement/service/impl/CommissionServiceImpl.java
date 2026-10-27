package com.soloeats.settlement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.settlement.domain.entity.CommissionRecord;
import com.soloeats.settlement.domain.entity.CommissionRule;
import com.soloeats.settlement.domain.enums.RuleScope;
import com.soloeats.settlement.mapper.CommissionRecordMapper;
import com.soloeats.settlement.mapper.CommissionRuleMapper;
import com.soloeats.settlement.service.CommissionService;
import com.soloeats.settlement.service.FundFlowService;
import com.soloeats.settlement.service.command.CommissionRuleSaveCommand;
import com.soloeats.settlement.service.view.CommissionRecordView;
import com.soloeats.settlement.service.view.CommissionResolveResult;
import com.soloeats.settlement.service.view.CommissionRuleView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 三级抽佣规则引擎实现。
 *
 * <p>命中顺序写死在 {@link RuleScope#hitPriority()}：活动级 > 商家级 > 品类级 > 全局默认。
 * 同一 scope 内由 SQL 按 {@code priority DESC, effective_from DESC, id DESC} 取首条。
 * 无任何规则命中时使用 {@code app.settlement.default-commission-rate}（默认 0.0600），
 * 并把 rule_id / rule_scope 记成 NULL，保证「未配置规则」与「配置了 0.0600」可区分。
 *
 * <p>金额一律 bigint 分，比例用 {@link BigDecimal} 保留 4 位并 HALF_UP；
 * 抽佣金额 = HALF_UP(base * rate) 取整到分，只算一次并落库，禁止多处重算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

    /** 内置默认佣金比例，仅用于配置缺失兜底。 */
    static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.0600");

    private static final int RATE_SCALE = 4;
    private static final int REMARK_MAX_LENGTH = 255;

    private final CommissionRuleMapper commissionRuleMapper;
    private final CommissionRecordMapper commissionRecordMapper;
    private final FundFlowService fundFlowService;

    /**
     * 规则未命中时的兜底费率。字段给了默认值，纯 Mockito 单测不注入也能工作；
     * Spring 启动时由 {@code @Value} 覆盖。
     */
    @Value("${app.settlement.default-commission-rate:0.0600}")
    private BigDecimal defaultCommissionRate = DEFAULT_COMMISSION_RATE;

    @Override
    public CommissionResolveResult resolve(Long merchantId, Long categoryId, Long promotionId,
                                           Long amount) {
        long baseAmount = amount == null ? 0L : amount;
        if (baseAmount < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "amount 不能为负数");
        }
        CommissionRule hit = findHit(merchantId, categoryId, promotionId, baseAmount);
        BigDecimal rate = hit == null ? normalizedDefaultRate() : scaleRate(hit.getCommissionRate());
        return new CommissionResolveResult(
                hit == null ? null : hit.getId(),
                hit == null ? null : hit.getRuleScope(),
                rate,
                baseAmount,
                calcCommission(baseAmount, rate)
        );
    }

    @Override
    public CommissionResolveResult resolveAndRecord(Order order, long baseAmount) {
        if (order == null || order.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "订单不能为空");
        }
        long base = Math.max(0L, baseAmount);
        Long categoryId = commissionRuleMapper.findOrderCategoryId(order.getId());
        Long promotionId = commissionRuleMapper.findOrderPromotionId(order.getId());
        CommissionResolveResult result = resolve(
                order.getMerchantId(), categoryId, promotionId, base);

        CommissionRecord record = new CommissionRecord();
        record.setOrderId(order.getId());
        record.setMerchantId(order.getMerchantId());
        record.setShopId(order.getShopId());
        record.setCategoryId(categoryId);
        record.setBaseAmount(result.baseAmount());
        record.setRuleId(result.ruleId());
        record.setRuleScope(result.ruleScope());
        record.setCommissionRate(result.commissionRate());
        record.setCommissionAmount(result.commissionAmount());
        try {
            commissionRecordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            // uk_order 兜底：同一订单重复结算时以已落库的明细为准，不重算、不重复记账
            CommissionRecord existing = commissionRecordMapper.findByOrderId(order.getId());
            if (existing == null) {
                throw ex;
            }
            log.warn("commission record exists orderId={} recordId={}", order.getId(), existing.getId());
            return CommissionResolveResult.of(existing);
        }
        fundFlowService.recordCommissionIncome(record);
        log.info("commission recorded orderId={} merchantId={} base={} rate={} amount={} ruleId={} scope={}",
                order.getId(), order.getMerchantId(), record.getBaseAmount(),
                record.getCommissionRate(), record.getCommissionAmount(),
                record.getRuleId(), record.getRuleScope());
        return result;
    }

    @Override
    public int bindSettlementBill(Long billId, List<Long> orderIds) {
        if (billId == null || orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        return commissionRecordMapper.bindSettlementBill(billId, orderIds);
    }

    @Override
    public CommissionRuleView create(CommissionRuleSaveCommand command) {
        CommissionRule rule = new CommissionRule();
        applyCommand(rule, command, true);
        commissionRuleMapper.insert(rule);
        log.info("commission rule created ruleId={} scope={} refId={} rate={}",
                rule.getId(), rule.getRuleScope(), rule.getRefId(), rule.getCommissionRate());
        return reload(rule);
    }

    @Override
    public CommissionRuleView update(Long id, CommissionRuleSaveCommand command) {
        CommissionRule rule = requireRule(id);
        applyCommand(rule, command, false);
        commissionRuleMapper.updateById(rule);
        log.info("commission rule updated ruleId={} scope={} refId={} rate={}",
                rule.getId(), rule.getRuleScope(), rule.getRefId(), rule.getCommissionRate());
        return reload(rule);
    }

    @Override
    public CommissionRuleView get(Long id) {
        return CommissionRuleView.of(requireRule(id));
    }

    @Override
    public PageResult<CommissionRuleView> pageRules(Integer ruleScope, Long refId, Integer status,
                                                    long pageNum, long pageSize) {
        if (ruleScope != null && !RuleScope.isValid(ruleScope)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "ruleScope 只能是 1全局 2品类 3商家 4活动");
        }
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);
        LambdaQueryWrapper<CommissionRule> wrapper = new LambdaQueryWrapper<CommissionRule>()
                .eq(CommissionRule::getDeleted, 0)
                .eq(ruleScope != null, CommissionRule::getRuleScope, ruleScope)
                .eq(refId != null, CommissionRule::getRefId, refId)
                .eq(status != null, CommissionRule::getStatus, status)
                .orderByAsc(CommissionRule::getRuleScope)
                .orderByDesc(CommissionRule::getPriority)
                .orderByDesc(CommissionRule::getId);
        Page<CommissionRule> page = commissionRuleMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(CommissionRuleView::of).toList(),
                page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public PageResult<CommissionRecordView> pageRecords(Long merchantId, Long categoryId,
                                                        LocalDate dateFrom, LocalDate dateTo,
                                                        long pageNum, long pageSize) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);
        LambdaQueryWrapper<CommissionRecord> wrapper = new LambdaQueryWrapper<CommissionRecord>()
                .eq(merchantId != null, CommissionRecord::getMerchantId, merchantId)
                .eq(categoryId != null, CommissionRecord::getCategoryId, categoryId)
                .ge(dateFrom != null, CommissionRecord::getCreatedAt,
                        dateFrom == null ? null : dateFrom.atStartOfDay())
                .lt(dateTo != null, CommissionRecord::getCreatedAt,
                        dateTo == null ? null : dateTo.plusDays(1).atStartOfDay())
                .orderByDesc(CommissionRecord::getId);
        Page<CommissionRecord> page = commissionRecordMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(CommissionRecordView::of).toList(),
                page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    /**
     * 按「活动 > 商家 > 品类 > 全局」逐级命中，首个命中即返回；
     * 每级都带生效期与金额区间过滤，由 SQL 完成。
     */
    private CommissionRule findHit(Long merchantId, Long categoryId, Long promotionId,
                                   long amount) {
        LocalDateTime now = LocalDateTime.now();
        for (RuleScope scope : RuleScope.hitPriority()) {
            switch (scope) {
                case PROMOTION -> {
                    if (promotionId != null) {
                        CommissionRule rule = commissionRuleMapper.findEffectiveByScopeAndRef(
                                scope.getCode(), promotionId, amount, now);
                        if (rule != null) {
                            return rule;
                        }
                    }
                }
                case MERCHANT -> {
                    if (merchantId != null) {
                        CommissionRule rule = commissionRuleMapper.findEffectiveByScopeAndRef(
                                scope.getCode(), merchantId, amount, now);
                        if (rule != null) {
                            return rule;
                        }
                    }
                }
                case CATEGORY -> {
                    if (categoryId != null) {
                        CommissionRule rule = commissionRuleMapper.findEffectiveByScopeAndRef(
                                scope.getCode(), categoryId, amount, now);
                        if (rule != null) {
                            return rule;
                        }
                    }
                }
                case GLOBAL -> {
                    CommissionRule rule = commissionRuleMapper.findEffectiveGlobal(amount, now);
                    if (rule != null) {
                        return rule;
                    }
                }
                default -> throw new BizException(ErrorCode.SYSTEM_ERROR, "未知 ruleScope");
            }
        }
        return null;
    }

    /**
     * 抽佣金额 = HALF_UP(base * rate)，取整到分。
     */
    private long calcCommission(long baseAmount, BigDecimal rate) {
        return BigDecimal.valueOf(baseAmount)
                .multiply(rate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
    }

    /**
     * 兜底费率标准化：4 位小数、HALF_UP，配置非法时直接报系统错误而不是静默按 0 抽佣。
     * 字段带 Java 默认值，配置缺失时不会为 null。
     */
    private BigDecimal normalizedDefaultRate() {
        BigDecimal scaled = defaultCommissionRate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0 || scaled.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "app.settlement.default-commission-rate 配置不合法：" + defaultCommissionRate);
        }
        return scaled;
    }

    private BigDecimal scaleRate(BigDecimal rate) {
        if (rate == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "commissionRate 不能为空");
        }
        BigDecimal scaled = rate.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) <= 0 || scaled.compareTo(BigDecimal.ONE) > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "commissionRate 必须大于 0 且不超过 1");
        }
        return scaled;
    }

    private CommissionRule requireRule(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "id 不能为空");
        }
        CommissionRule rule = commissionRuleMapper.selectById(id);
        if (rule == null || (rule.getDeleted() != null && rule.getDeleted() == 1)) {
            throw new BizException(ErrorCode.COMMISSION_RULE_NOT_FOUND);
        }
        return rule;
    }

    /**
     * 插入/更新后回读一次，取数据库生成的时间戳；回读失败时退回内存对象。
     */
    private CommissionRuleView reload(CommissionRule rule) {
        CommissionRule saved = rule.getId() == null ? null : commissionRuleMapper.selectById(rule.getId());
        return CommissionRuleView.of(saved == null ? rule : saved);
    }

    /**
     * 校验并写入规则字段。creating = false 时允许省略 effectiveFrom（沿用原值）。
     */
    private void applyCommand(CommissionRule rule, CommissionRuleSaveCommand command,
                              boolean creating) {
        if (command == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则参数不能为空");
        }
        RuleScope scope;
        try {
            scope = RuleScope.of(command.ruleScope());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, ex.getMessage());
        }
        Long refId = null;
        if (scope.requireRefId()) {
            if (command.refId() == null || command.refId() <= 0L) {
                throw new BizException(ErrorCode.PARAM_INVALID,
                        scope.getDescription() + "规则必须指定 refId");
            }
            refId = command.refId();
        }
        BigDecimal rate = scaleRate(command.commissionRate());

        Long minAmount = command.minAmount();
        Long maxAmount = command.maxAmount();
        if (minAmount != null && minAmount < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "minAmount 不能为负数");
        }
        if (maxAmount != null && maxAmount < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "maxAmount 不能为负数");
        }
        if (minAmount != null && maxAmount != null && minAmount > maxAmount) {
            throw new BizException(ErrorCode.PARAM_INVALID, "minAmount 不能大于 maxAmount");
        }

        LocalDateTime effectiveFrom = command.effectiveFrom();
        if (effectiveFrom == null) {
            effectiveFrom = creating ? LocalDateTime.now() : rule.getEffectiveFrom();
        }
        LocalDateTime effectiveTo = command.effectiveTo();
        if (effectiveFrom != null && effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "effectiveTo 必须晚于 effectiveFrom");
        }

        Integer status = command.status();
        if (status == null) {
            status = creating ? Integer.valueOf(1) : rule.getStatus();
        }
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "status 只能是 0停用 或 1启用");
        }

        Integer priority = command.priority();
        if (priority == null) {
            priority = creating ? Integer.valueOf(0) : rule.getPriority();
        }
        if (priority == null) {
            priority = Integer.valueOf(0);
        }

        if (command.remark() != null && command.remark().length() > REMARK_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "remark 不能超过 255 个字符");
        }

        rule.setRuleScope(scope.getCode());
        rule.setRefId(refId);
        rule.setCommissionRate(rate);
        rule.setMinAmount(minAmount);
        rule.setMaxAmount(maxAmount);
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(effectiveTo);
        rule.setPriority(priority);
        rule.setStatus(status);
        rule.setRemark(command.remark());
        if (creating) {
            rule.setDeleted(0);
        }
    }

    private long normalizePageNum(long pageNum) {
        return pageNum < 1L ? 1L : pageNum;
    }

    private long normalizePageSize(long pageSize) {
        return pageSize < 1L ? 20L : Math.min(pageSize, 100L);
    }
}
