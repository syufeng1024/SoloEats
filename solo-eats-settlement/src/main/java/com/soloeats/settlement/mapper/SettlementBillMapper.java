package com.soloeats.settlement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.order.domain.entity.RefundOrder;
import com.soloeats.settlement.domain.entity.SettlementBill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SettlementBillMapper extends BaseMapper<SettlementBill> {

    @Select("""
            SELECT *
            FROM t_settlement_bill
            WHERE merchant_id = #{merchantId}
              AND bill_date = #{billDate}
            LIMIT 1
            """)
    SettlementBill findByMerchantAndDate(@Param("merchantId") Long merchantId,
                                         @Param("billDate") LocalDate billDate);

    /**
     * 该商家最近一张带负向结转（{@code carry_forward_amount < 0}）且未被驳回的账单。
     *
     * <p>用于下一期账单承接上期负项（技术架构 3.10.2：当期应结为负时不在本期扣款，
     * 生成 {@code carry_forward_amount} 进入下一期负项）。是否已承接由
     * {@link SettlementItemMapper#countByTypeAndSource} 以 {@code item_type=5 + source_id=账单ID} 判定，
     * 因为账单生成后不可修改，不能回写上期账单来表示「已承接」。
     */
    @Select("""
            SELECT *
            FROM t_settlement_bill
            WHERE merchant_id = #{merchantId}
              AND carry_forward_amount < 0
              AND status <> 3
            ORDER BY bill_date DESC, id DESC
            LIMIT 1
            """)
    SettlementBill findLatestCarryForward(@Param("merchantId") Long merchantId);

    /**
     * 本期需要冲抵的成功退款：退款成功时间落在账单周期内，且所属订单**已完成结算**。
     *
     * <p>技术架构 3.10.4：已结算订单退款生成 {@code item_type=4} 负项挂下一期账单。
     * 未结算就退款的订单本就不会进入账单（生成条件含 {@code refund_status=0}），故不在此列，
     * 判定直接下推到 SQL 的 {@code o.settlement_status = 1}，避免逐单回查。
     */
    @Select("""
            SELECT r.*
            FROM t_refund_order r
            INNER JOIN t_order o ON o.id = r.order_id
            WHERE r.status = #{status}
              AND r.success_at >= #{periodStart}
              AND r.success_at < #{periodEnd}
              AND o.settlement_status = 1
            ORDER BY r.id ASC
            """)
    List<RefundOrder> findSettleableRefunds(@Param("status") Integer status,
                                            @Param("periodStart") LocalDateTime periodStart,
                                            @Param("periodEnd") LocalDateTime periodEnd);
}
