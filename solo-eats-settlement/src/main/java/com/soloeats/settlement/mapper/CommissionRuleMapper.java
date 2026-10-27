package com.soloeats.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.settlement.domain.entity.CommissionRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 抽佣规则读写。
 *
 * <p>另外两个查询用于「订单维度的规则归属解析」：抽佣基数与品类/活动归属都来自
 * 订单侧表（t_order_item / t_spu / t_order_discount / t_promotion）。
 * settlement → order 的依赖方向本身是既有约定，这里只读表、不引入跨模块实体，
 * 避免 settlement 反向依赖 product / marketing。
 */
@Mapper
public interface CommissionRuleMapper extends BaseMapper<CommissionRule> {

    /**
     * 按 scope + refId 取生效规则首条。
     *
     * <p>生效判定：status = 1、未删除、now 落在 [effective_from, effective_to] 内
     * （effective_to 为 NULL 视为长期有效），且 amount 落在 min_amount/max_amount 区间
     * （NULL 视为不限）。同 scope 多条时按 priority DESC, effective_from DESC, id DESC 取第一条。
     *
     * @param amount 订单金额（分），不能为 null
     */
    @Select("""
            SELECT *
            FROM t_commission_rule
            WHERE rule_scope = #{ruleScope}
              AND ref_id = #{refId}
              AND status = 1
              AND deleted = 0
              AND effective_from <= #{now}
              AND (effective_to IS NULL OR effective_to >= #{now})
              AND (min_amount IS NULL OR min_amount <= #{amount})
              AND (max_amount IS NULL OR max_amount >= #{amount})
            ORDER BY priority DESC, effective_from DESC, id DESC
            LIMIT 1
            """)
    CommissionRule findEffectiveByScopeAndRef(@Param("ruleScope") Integer ruleScope,
                                             @Param("refId") Long refId,
                                             @Param("amount") Long amount,
                                             @Param("now") LocalDateTime now);

    /**
     * 全局默认规则（rule_scope = 1，ref_id 为空）的生效首条，语义同上。
     */
    @Select("""
            SELECT *
            FROM t_commission_rule
            WHERE rule_scope = 1
              AND (ref_id IS NULL OR ref_id = 0)
              AND status = 1
              AND deleted = 0
              AND effective_from <= #{now}
              AND (effective_to IS NULL OR effective_to >= #{now})
              AND (min_amount IS NULL OR min_amount <= #{amount})
              AND (max_amount IS NULL OR max_amount >= #{amount})
            ORDER BY priority DESC, effective_from DESC, id DESC
            LIMIT 1
            """)
    CommissionRule findEffectiveGlobal(@Param("amount") Long amount,
                                       @Param("now") LocalDateTime now);

    /**
     * 订单所属品类：取订单第一件商品（t_order_item.id 最小）的 SPU 品类。
     * 订单无明细或缺品类时返回 null，抽佣命中会自动跳过品类级。
     */
    @Select("""
            SELECT spu.category_id
            FROM t_order_item oi
            JOIN t_spu spu ON spu.id = oi.spu_id
            WHERE oi.order_id = #{orderId}
              AND spu.category_id IS NOT NULL
            ORDER BY oi.id
            LIMIT 1
            """)
    Long findOrderCategoryId(@Param("orderId") Long orderId);

    /**
     * 订单命中的营销活动：取满减/折扣类优惠（discount_type = 2）中活动优先级最高的一个，
     * 用于活动级抽佣规则命中。订单没有进行中的活动时返回 null。
     */
    @Select("""
            SELECT d.source_id
            FROM t_order_discount d
            JOIN t_promotion p ON p.id = d.source_id
            WHERE d.order_id = #{orderId}
              AND d.discount_type = 2
              AND p.status = 1
            ORDER BY p.priority DESC, p.id DESC
            LIMIT 1
            """)
    Long findOrderPromotionId(@Param("orderId") Long orderId);
}
