package com.soloeats.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.order.domain.entity.RefundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefundOrderMapper extends BaseMapper<RefundOrder> {

    @Select("SELECT * FROM t_refund_order WHERE idempotency_key = #{key} LIMIT 1")
    RefundOrder findByIdempotencyKey(@Param("key") String key);

    @Select("""
            SELECT *
            FROM t_refund_order
            WHERE order_id = #{orderId}
            ORDER BY id DESC
            LIMIT 1
            """)
    RefundOrder findLatestByOrderId(@Param("orderId") Long orderId);

    @Update("""
            UPDATE t_refund_order
            SET status = 30,
                channel_refund_no = #{channelRefundNo},
                success_at = NOW(3),
                updated_at = NOW(3)
            WHERE id = #{refundId}
              AND status = 20
            """)
    int markSuccess(@Param("refundId") Long refundId,
                    @Param("channelRefundNo") String channelRefundNo);
}
