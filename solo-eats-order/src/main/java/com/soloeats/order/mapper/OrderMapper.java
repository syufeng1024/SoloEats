package com.soloeats.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.order.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    @Update("""
            UPDATE t_order
            SET status = #{toStatus},
                version = version + 1,
                updated_at = NOW(3)
            WHERE id = #{orderId}
              AND status = #{fromStatus}
              AND version = #{version}
            """)
    int updateStatus(@Param("orderId") Long orderId,
                     @Param("fromStatus") int fromStatus,
                     @Param("toStatus") int toStatus,
                     @Param("version") int version);

    @Update("""
            UPDATE t_order
            SET payment_status = 1,
                updated_at = NOW(3)
            WHERE id = #{orderId}
              AND status = 10
            """)
    int markPaid(@Param("orderId") Long orderId);

    @Update("""
            UPDATE t_order
            SET completed_at = NOW(3),
                settle_eligible_at = #{settleEligibleAt},
                updated_at = NOW(3)
            WHERE id = #{orderId}
              AND status = 60
            """)
    int markCompleted(@Param("orderId") Long orderId,
                      @Param("settleEligibleAt") LocalDateTime settleEligibleAt);

    @Update("""
            UPDATE t_order
            SET payment_status = 2,
                refund_status = 2,
                refunded_amount = #{amount},
                updated_at = NOW(3)
            WHERE id = #{orderId}
              AND status = 70
            """)
    int markRefunded(@Param("orderId") Long orderId,
                     @Param("amount") Long amount);

    @Update("""
            UPDATE t_order
            SET cancel_reason = #{reason},
                cancelled_at = NOW(3),
                updated_at = NOW(3)
            WHERE id = #{orderId}
              AND status = 70
            """)
    int markCancelled(@Param("orderId") Long orderId,
                      @Param("reason") String reason);
}
