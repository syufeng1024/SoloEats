package com.soloeats.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.payment.domain.entity.PaymentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    @Select("""
            SELECT *
            FROM t_payment_order
            WHERE order_id = #{orderId}
              AND status IN (0, 10)
            ORDER BY id DESC
            LIMIT 1
            """)
    PaymentOrder findActiveByOrderId(@Param("orderId") Long orderId);

    @Select("""
            SELECT *
            FROM t_payment_order
            WHERE order_id = #{orderId}
              AND status = 20
            ORDER BY id DESC
            LIMIT 1
            """)
    PaymentOrder findSuccessByOrderId(@Param("orderId") Long orderId);

    @Select("""
            SELECT *
            FROM t_payment_order
            WHERE order_id = #{orderId}
            ORDER BY id DESC
            LIMIT 1
            """)
    PaymentOrder findLatestByOrderId(@Param("orderId") Long orderId);

    @Update("""
            UPDATE t_payment_order
            SET status = 20,
                channel_trade_no = #{tradeNo},
                paid_at = NOW(3),
                updated_at = NOW(3)
            WHERE id = #{paymentId}
              AND status IN (0, 10)
            """)
    int markSuccess(@Param("paymentId") Long paymentId,
                    @Param("tradeNo") String tradeNo);

    @Update("""
            UPDATE t_payment_order
            SET status = 40,
                updated_at = NOW(3)
            WHERE order_id = #{orderId}
              AND status IN (0, 10)
            """)
    int closeByOrderId(@Param("orderId") Long orderId);

    @Update("""
            UPDATE t_payment_order
            SET status = 60,
                updated_at = NOW(3)
            WHERE order_id = #{orderId}
              AND status IN (20, 60)
            """)
    int markRefunded(@Param("orderId") Long orderId);
}
