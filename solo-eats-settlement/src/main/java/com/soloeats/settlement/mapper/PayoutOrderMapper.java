package com.soloeats.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.settlement.domain.entity.PayoutOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 提现单读写。可提现余额涉及的三张表都在结算域内直读，不跨模块依赖实体：
 * t_settlement_bill（商家已到账账单）、t_rider_income（骑手收入）、t_payout_order（占用中的提现）。
 */
@Mapper
public interface PayoutOrderMapper extends BaseMapper<PayoutOrder> {

    /**
     * 商家可提现基数：仅统计已打款到账（status=30）的结算单。
     */
    @Select("""
            SELECT COALESCE(SUM(payable_amount), 0)
            FROM t_settlement_bill
            WHERE merchant_id = #{merchantId}
              AND status = 30
            """)
    Long sumMerchantSettledPayable(@Param("merchantId") Long merchantId);

    /**
     * 骑手累计收入。t_rider_income.status 为 0待结算/1已结算，
     * P0 提现口径按收入全额计算，不区分结算状态。
     */
    @Select("""
            SELECT COALESCE(SUM(total_amount), 0)
            FROM t_rider_income
            WHERE rider_id = #{riderId}
            """)
    Long sumRiderIncome(@Param("riderId") Long riderId);

    /**
     * 已占用额度：待审核/审核通过/打款中/已到账都计入，已驳回不计入。
     */
    @Select("""
            SELECT COALESCE(SUM(amount), 0)
            FROM t_payout_order
            WHERE subject_type = #{subjectType}
              AND subject_id = #{subjectId}
              AND status IN (0, 10, 20, 30)
            """)
    Long sumActivePayoutAmount(@Param("subjectType") Integer subjectType,
                               @Param("subjectId") Long subjectId);
}
