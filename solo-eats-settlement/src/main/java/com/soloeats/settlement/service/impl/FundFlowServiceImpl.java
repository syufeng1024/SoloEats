package com.soloeats.settlement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soloeats.common.api.PageResult;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.settlement.domain.entity.CommissionRecord;
import com.soloeats.settlement.domain.entity.FundFlow;
import com.soloeats.settlement.domain.entity.PayoutOrder;
import com.soloeats.settlement.domain.enums.FlowType;
import com.soloeats.settlement.mapper.FundFlowMapper;
import com.soloeats.settlement.service.FundFlowService;
import com.soloeats.settlement.service.view.FundFlowSummary;
import com.soloeats.settlement.service.view.FundFlowView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 平台资金流水实现。
 *
 * <p>幂等：先按 uk_biz(flow_type, biz_type, biz_id) 预查，再兜底捕获
 * {@link DuplicateKeyException}（并发下预查可能都通过），重复记账静默跳过。
 * 平台没有统一资金账户，balance_after 一律不写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundFlowServiceImpl implements FundFlowService {

    private static final int REMARK_MAX_LENGTH = 255;

    /** 汇总区间左端哨兵：不限起始日期时用，避免向 SQL 绑定 null。 */
    private static final LocalDateTime MIN_TIME = LocalDateTime.of(1970, 1, 1, 0, 0);
    /** 汇总区间右端哨兵：不限结束日期时用，MySQL datetime 上限为 9999-12-31。 */
    private static final LocalDateTime MAX_TIME = LocalDateTime.of(9999, 12, 31, 0, 0);

    private final FundFlowMapper fundFlowMapper;

    @Override
    public void record(Integer flowType, String bizType, Long bizId, Integer direction,
                       long amount, String remark) {
        if (!FlowType.isValid(flowType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "flowType 只能是 1~6");
        }
        if (bizType == null || bizType.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizType 不能为空");
        }
        if (bizId == null || bizId <= 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "bizId 不合法");
        }
        if (!FlowType.isValidDirection(direction)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "direction 只能是 1入 或 2出");
        }
        if (amount < 0L) {
            throw new BizException(ErrorCode.PARAM_INVALID, "流水金额不能为负数");
        }
        if (amount == 0L) {
            // 0 元流水没有账务意义，直接跳过，避免污染汇总
            log.debug("skip zero fund flow flowType={} bizType={} bizId={}", flowType, bizType, bizId);
            return;
        }
        String normalizedRemark = normalizeRemark(remark);
        if (fundFlowMapper.countByBiz(flowType, bizType, bizId) > 0) {
            log.debug("fund flow exists flowType={} bizType={} bizId={}", flowType, bizType, bizId);
            return;
        }
        FundFlow flow = new FundFlow();
        flow.setFlowType(flowType);
        flow.setBizType(bizType);
        flow.setBizId(bizId);
        flow.setDirection(direction);
        flow.setAmount(amount);
        flow.setRemark(normalizedRemark);
        try {
            fundFlowMapper.insert(flow);
            log.info("fund flow recorded flowId={} flowType={} bizType={} bizId={} amount={}",
                    flow.getId(), flowType, bizType, bizId, amount);
        } catch (DuplicateKeyException ex) {
            // 并发下两个请求同时通过预查，uk_biz 兜底，后到的按幂等处理
            log.debug("fund flow duplicated flowType={} bizType={} bizId={}", flowType, bizType, bizId);
        }
    }

    @Override
    public void recordCommissionIncome(CommissionRecord record) {
        if (record == null || record.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "抽佣明细不能为空");
        }
        record(FlowType.COMMISSION_INCOME.getCode(), BIZ_TYPE_COMMISSION_RECORD, record.getId(),
                FlowType.COMMISSION_INCOME.getDefaultDirection(),
                record.getCommissionAmount() == null ? 0L : record.getCommissionAmount(),
                "订单 " + record.getOrderId() + " 抽佣收入");
    }

    @Override
    public void recordPayoutExpense(PayoutOrder order) {
        if (order == null || order.getId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "提现单不能为空");
        }
        record(FlowType.PAYOUT_EXPENSE.getCode(), BIZ_TYPE_PAYOUT_ORDER, order.getId(),
                FlowType.PAYOUT_EXPENSE.getDefaultDirection(),
                order.getAmount() == null ? 0L : order.getAmount(),
                "提现单 " + order.getPayoutNo() + " 到账支出");
    }

    @Override
    public PageResult<FundFlowView> page(Integer flowType, LocalDate dateFrom, LocalDate dateTo,
                                         long pageNum, long pageSize) {
        if (flowType != null && !FlowType.isValid(flowType)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "flowType 只能是 1~6");
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }
        long normalizedPageNum = normalizePageNum(pageNum);
        long normalizedPageSize = normalizePageSize(pageSize);
        LambdaQueryWrapper<FundFlow> wrapper = new LambdaQueryWrapper<FundFlow>()
                .eq(flowType != null, FundFlow::getFlowType, flowType)
                .ge(dateFrom != null, FundFlow::getCreatedAt, startOfDay(dateFrom))
                .lt(dateTo != null, FundFlow::getCreatedAt, startOfNextDay(dateTo))
                .orderByDesc(FundFlow::getId);
        Page<FundFlow> page = fundFlowMapper.selectPage(
                new Page<>(normalizedPageNum, normalizedPageSize), wrapper);
        return PageResult.of(page.getRecords().stream().map(FundFlowView::of).toList(),
                page.getTotal(), normalizedPageNum, normalizedPageSize);
    }

    @Override
    public FundFlowSummary summary(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dateFrom 不能晚于 dateTo");
        }
        Map<String, Object> row = fundFlowMapper.sumByFlowType(
                dateFrom == null ? MIN_TIME : startOfDay(dateFrom),
                dateTo == null ? MAX_TIME : startOfNextDay(dateTo));
        if (row == null || row.isEmpty()) {
            return FundFlowSummary.empty();
        }
        return FundFlowSummary.of(
                summaryLong(row, "commission_income"),
                summaryLong(row, "settlement_expense"),
                summaryLong(row, "subsidy_cost"),
                summaryLong(row, "discount_cost"),
                summaryLong(row, "payout_expense"),
                summaryLong(row, "refund_offset")
        );
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String trimmed = remark.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > REMARK_MAX_LENGTH
                ? trimmed.substring(0, REMARK_MAX_LENGTH)
                : trimmed;
    }

    private LocalDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    private LocalDateTime startOfNextDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay();
    }

    private long normalizePageNum(long pageNum) {
        return pageNum < 1L ? 1L : pageNum;
    }

    private long normalizePageSize(long pageSize) {
        return pageSize < 1L ? 20L : Math.min(pageSize, 100L);
    }

    /**
     * MySQL 返回的列标签大小写不保证，这里做一次忽略大小写的取值。
     */
    private long summaryLong(Map<String, Object> row, String alias) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(alias)) {
                Object value = entry.getValue();
                return value instanceof Number number ? number.longValue() : 0L;
            }
        }
        return 0L;
    }
}
