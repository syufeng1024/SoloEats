package com.soloeats.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.settlement.domain.entity.FundFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 平台资金流水读写。汇总用一条条件聚合 SQL，避免 6 次单独求和。
 */
@Mapper
public interface FundFlowMapper extends BaseMapper<FundFlow> {

    /**
     * 幂等前置查询：同一业务单据同一流水类型是否已记过账。
     */
    @Select("""
            SELECT COUNT(*)
            FROM t_fund_flow
            WHERE flow_type = #{flowType}
              AND biz_type = #{bizType}
              AND biz_id = #{bizId}
            """)
    int countByBiz(@Param("flowType") Integer flowType,
                   @Param("bizType") String bizType,
                   @Param("bizId") Long bizId);

    /**
     * 按流水类型汇总金额。区间为左闭右开 [dateFrom, dateTo)。
     *
     * <p>调用方必须传非空区间（不限时用哨兵时间），避免 MySQL 驱动对 null 参数
     * 使用 jdbcType=OTHER 报错。
     */
    @Select("""
            SELECT
              COALESCE(SUM(CASE WHEN flow_type = 1 THEN amount ELSE 0 END), 0) AS commission_income,
              COALESCE(SUM(CASE WHEN flow_type = 2 THEN amount ELSE 0 END), 0) AS settlement_expense,
              COALESCE(SUM(CASE WHEN flow_type = 3 THEN amount ELSE 0 END), 0) AS subsidy_cost,
              COALESCE(SUM(CASE WHEN flow_type = 4 THEN amount ELSE 0 END), 0) AS discount_cost,
              COALESCE(SUM(CASE WHEN flow_type = 5 THEN amount ELSE 0 END), 0) AS payout_expense,
              COALESCE(SUM(CASE WHEN flow_type = 6 THEN amount ELSE 0 END), 0) AS refund_offset
            FROM t_fund_flow
            WHERE created_at >= #{dateFrom}
              AND created_at < #{dateTo}
            """)
    Map<String, Object> sumByFlowType(@Param("dateFrom") LocalDateTime dateFrom,
                                      @Param("dateTo") LocalDateTime dateTo);
}
