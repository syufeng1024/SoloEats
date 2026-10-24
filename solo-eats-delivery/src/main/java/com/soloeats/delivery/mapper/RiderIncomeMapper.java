package com.soloeats.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.delivery.domain.entity.RiderIncome;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Map;

@Mapper
public interface RiderIncomeMapper extends BaseMapper<RiderIncome> {

    /**
     * 今日/本周/本月收入与今日单量一次聚合，金额单位分。
     * 本周按自然周（周一至今日），本月按自然月（1 日至今日）。
     */
    @Select("""
            SELECT
              COALESCE(SUM(CASE WHEN settlement_date = #{today}
                                THEN total_amount ELSE 0 END), 0) AS todayAmount,
              COALESCE(SUM(CASE WHEN settlement_date = #{today}
                                THEN 1 ELSE 0 END), 0) AS todayOrders,
              COALESCE(SUM(CASE WHEN settlement_date >= #{weekStart}
                                 AND settlement_date <= #{today}
                                THEN total_amount ELSE 0 END), 0) AS weekAmount,
              COALESCE(SUM(CASE WHEN settlement_date >= #{monthStart}
                                 AND settlement_date <= #{today}
                                THEN total_amount ELSE 0 END), 0) AS monthAmount
            FROM t_rider_income
            WHERE rider_id = #{riderId}
            """)
    Map<String, Object> selectOverview(@Param("riderId") Long riderId,
                                       @Param("today") LocalDate today,
                                       @Param("weekStart") LocalDate weekStart,
                                       @Param("monthStart") LocalDate monthStart);
}
