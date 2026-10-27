package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.PromotionRule;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /**
     * 活动的全部档位，按 sort 升序。供后台详情与 C 端列表回显，不参与命中判定。
     */
    @Select("""
            SELECT *
            FROM t_promotion_rule
            WHERE promotion_id = #{promotionId}
            ORDER BY sort ASC, id ASC
            """)
    List<PromotionRule> selectByPromotionId(@Param("promotionId") Long promotionId);

    /**
     * 物理删除活动档位。t_promotion_rule 是纯子表，无 deleted 列，
     * 编辑时先删后插，避免残留已下线的档位。
     */
    @Delete("DELETE FROM t_promotion_rule WHERE promotion_id = #{promotionId}")
    int deleteByPromotionId(@Param("promotionId") Long promotionId);
}
