package com.soloeats.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.settlement.domain.entity.SettlementItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SettlementItemMapper extends BaseMapper<SettlementItem> {

    /**
     * 某订单在账单中的原始应结金额 = 商品项 + 佣金项（佣金项以负数落库，求和即为净额）。
     *
     * <p>退款冲抵以「该订单原本实结给商家的金额」为基数，而不是用户退款额：
     * 用户退款额含配送费（归平台）且受平台券影响，按它冲抵会让商家多承担平台的钱。
     * 按原应结金额冲抵，可保证「已结算订单全额退款后，商家该单净额为 0」这一资金恒等式。
     */
    @Select("""
            SELECT COALESCE(SUM(amount), 0)
            FROM t_settlement_item
            WHERE order_id = #{orderId}
              AND item_type IN (1, 2)
            """)
    long sumOrderSettleAmount(@Param("orderId") Long orderId);

    /**
     * 幂等判定：某类明细是否已针对同一来源生成过。
     *
     * <p>退款冲抵用 {@code item_type=4 + source_id=退款单ID}，负项承接用
     * {@code item_type=5 + source_id=上期账单ID}，避免同一笔资金被重复冲抵或重复承接。
     */
    @Select("""
            SELECT COUNT(1)
            FROM t_settlement_item
            WHERE item_type = #{itemType}
              AND source_id = #{sourceId}
            """)
    int countByTypeAndSource(@Param("itemType") Integer itemType,
                             @Param("sourceId") Long sourceId);
}
