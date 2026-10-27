package com.soloeats.settlement.service;

import com.soloeats.common.api.PageResult;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.settlement.service.command.CommissionRuleSaveCommand;
import com.soloeats.settlement.service.view.CommissionRecordView;
import com.soloeats.settlement.service.view.CommissionResolveResult;
import com.soloeats.settlement.service.view.CommissionRuleView;

import java.time.LocalDate;
import java.util.List;

/**
 * 三级抽佣规则引擎：规则命中、抽佣落库、规则维护与抽佣明细查询。
 *
 * <p>命中优先级固定为「活动级 > 商家级 > 品类级 > 全局默认」，同 scope 内取
 * {@code priority DESC, effective_from DESC} 第一条；无任何规则命中时回落
 * {@code app.settlement.default-commission-rate}（默认 0.0600）且 rule_id 记 NULL。
 */
public interface CommissionService {

    /**
     * 规则命中试算，不落库。amount 为抽佣基数（分）。
     */
    CommissionResolveResult resolve(Long merchantId, Long categoryId, Long promotionId, Long amount);

    /**
     * 结算时调用：命中规则 + 落 t_commission_record（uk_order 幂等）并记佣金收入流水。
     *
     * @param order      订单，提供 orderId/merchantId/shopId，用于反查品类与活动归属
     * @param baseAmount 抽佣基数（分），由调用方按既有口径算好后传入
     * @return 命中结果（含 ruleId/ruleScope/rate/amount）
     */
    CommissionResolveResult resolveAndRecord(Order order, long baseAmount);

    /**
     * 结算单生成后回填抽佣明细的归属账单，只回填空值。
     */
    int bindSettlementBill(Long billId, List<Long> orderIds);

    /**
     * 新建规则。
     */
    CommissionRuleView create(CommissionRuleSaveCommand command);

    /**
     * 编辑规则，按 id 查不到（或已删除）抛 60003。
     */
    CommissionRuleView update(Long id, CommissionRuleSaveCommand command);

    /**
     * 规则详情，按 id 查不到（或已删除）抛 60003。
     */
    CommissionRuleView get(Long id);

    /**
     * 规则分页。
     */
    PageResult<CommissionRuleView> pageRules(Integer ruleScope, Long refId, Integer status,
                                             long pageNum, long pageSize);

    /**
     * 抽佣明细分页：可按商家、品类、日期（按明细生成时间）筛选。
     */
    PageResult<CommissionRecordView> pageRecords(Long merchantId, Long categoryId,
                                                 LocalDate dateFrom, LocalDate dateTo,
                                                 long pageNum, long pageSize);
}
