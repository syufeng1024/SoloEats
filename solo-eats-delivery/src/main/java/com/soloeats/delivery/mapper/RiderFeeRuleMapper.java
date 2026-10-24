package com.soloeats.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.delivery.domain.entity.RiderFeeRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

@Mapper
public interface RiderFeeRuleMapper extends BaseMapper<RiderFeeRule> {

    /** 骑手专属费率：启用且在有效期内，取最新一条。 */
    @Select("""
            SELECT * FROM t_rider_fee_rule
            WHERE rider_id = #{riderId}
              AND status = 0
              AND (effective_from IS NULL OR effective_from <= #{date})
              AND (effective_to IS NULL OR effective_to >= #{date})
            ORDER BY id DESC
            LIMIT 1
            """)
    RiderFeeRule findActiveByRider(@Param("riderId") Long riderId,
                                   @Param("date") LocalDate date);

    /** 全局费率：rider_id IS NULL，条件同上。 */
    @Select("""
            SELECT * FROM t_rider_fee_rule
            WHERE rider_id IS NULL
              AND status = 0
              AND (effective_from IS NULL OR effective_from <= #{date})
              AND (effective_to IS NULL OR effective_to >= #{date})
            ORDER BY id DESC
            LIMIT 1
            """)
    RiderFeeRule findActiveGlobal(@Param("date") LocalDate date);
}
