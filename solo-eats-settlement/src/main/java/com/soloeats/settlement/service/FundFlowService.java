package com.soloeats.settlement.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.settlement.domain.entity.CommissionRecord;
import com.soloeats.settlement.domain.entity.PayoutOrder;
import com.soloeats.settlement.service.view.FundFlowSummary;
import com.soloeats.settlement.service.view.FundFlowView;

import java.time.LocalDate;

/**
 * 平台资金流水：写入（幂等）、分页查询、汇总。
 *
 * <p>写入幂等由 t_fund_flow 的 uk_biz(flow_type, biz_type, biz_id) 保证，
 * 重复记账直接跳过而不是报错，便于打款/到账等动作被重复触发时保持幂等。
 */
public interface FundFlowService {

    String BIZ_TYPE_COMMISSION_RECORD = "commission_record";
    String BIZ_TYPE_PAYOUT_ORDER = "payout_order";
    String BIZ_TYPE_SETTLEMENT_BILL = "settlement_bill";

    /**
     * 记一条流水。flowType / direction 取值见 {@code FlowType}；
     * 同一 (flowType, bizType, bizId) 已存在时静默跳过。
     */
    void record(Integer flowType, String bizType, Long bizId, Integer direction,
                long amount, String remark);

    /**
     * 抽佣入账：佣金收入（flow_type = 1），业务单据为抽佣明细。
     */
    void recordCommissionIncome(CommissionRecord record);

    /**
     * 提现到账：提现支出（flow_type = 5），业务单据为提现单。
     */
    void recordPayoutExpense(PayoutOrder order);

    /**
     * 平台资金流水分页，可按流水类型与日期（左闭右开）筛选。
     */
    PageResult<FundFlowView> page(Integer flowType, LocalDate dateFrom, LocalDate dateTo,
                                  long pageNum, long pageSize);

    /**
     * 资金汇总。无数据时返回全 0 汇总，不返回 null。
     */
    FundFlowSummary summary(LocalDate dateFrom, LocalDate dateTo);
}
