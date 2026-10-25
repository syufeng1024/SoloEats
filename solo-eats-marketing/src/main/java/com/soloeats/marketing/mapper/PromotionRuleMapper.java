package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.PromotionRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PromotionRuleMapper extends BaseMapper<PromotionRule> {

    /**
     * 取该活动下 threshold_amount &lt;= goodsAmount 的最大门槛档。
     * P0 只有一个档位，按最大门槛实现，阶段A 补阶梯时无需改调用方。
     */
    @Select("""
            SELECT *
            FROM t_promotion_rule
            WHERE promotion_id = #{promotionId}
              AND threshold_amount <= #{goodsAmount}
            ORDER BY threshold_amount DESC, sort ASC, id ASC
            LIMIT 1
            """)
    PromotionRule selectBestRule(@Param("promotionId") Long promotionId,
                                 @Param("goodsAmount") Long goodsAmount);
}
