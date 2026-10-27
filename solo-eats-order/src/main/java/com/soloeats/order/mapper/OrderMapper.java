package com.soloeats.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.order.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 商家工作台计数。5 个指标在同一条 SQL 内用条件聚合完成，避免多次 count 往返。
     * shopId 为空时不限门店；店铺账号带 shopId 时收窄到该门店。
     */
    @Select("""
            <script>
            SELECT
              COALESCE(SUM(CASE WHEN created_at &gt;= #{dayStart} THEN 1 ELSE 0 END), 0) AS today_order_count,
              COALESCE(SUM(CASE WHEN status = 10 THEN 1 ELSE 0 END), 0) AS pending_accept_count,
              COALESCE(SUM(CASE WHEN status = 20 THEN 1 ELSE 0 END), 0) AS preparing_count,
              COALESCE(SUM(CASE WHEN status = 31 AND fulfillment_type = 2 THEN 1 ELSE 0 END), 0) AS wait_pickup_count,
              COALESCE(SUM(CASE WHEN status = 60 AND completed_at &gt;= #{dayStart} THEN 1 ELSE 0 END), 0) AS completed_today_count
            FROM t_order
            WHERE merchant_id = #{merchantId}
            <if test="shopId != null">AND shop_id = #{shopId}</if>
            </script>
            """)
    Map<String, Object> selectMerchantDashboard(@Param("merchantId") Long merchantId,
                                                @Param("shopId") Long shopId,
                                                @Param("dayStart") LocalDateTime dayStart);

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

    /**
     * 自取订单生成取餐码。核销时按 orderId 查订单再比对码，
     * 因此取餐码不要求全局唯一，同日同店重复也不影响核销正确性。
     */
    @Update("""
            UPDATE t_order
            SET pickup_code = #{pickupCode},
                pickup_code_date = #{pickupDate},
                pickup_code_status = 0,
                updated_at = NOW(3)
            WHERE id = #{orderId}
            """)
    int assignPickupCode(@Param("orderId") Long orderId,
                         @Param("pickupCode") String pickupCode,
                         @Param("pickupDate") LocalDate pickupDate);

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

    /**
     * 售后期内对「已完成」订单退款（规格书 8.8）：订单保持 COMPLETED 终态，
     * 只回写退款状态（2=全额）与退款金额。与 markRefunded（取消路径，status=70）区分，
     * 同样以 status 作为条件更新，防止并发覆盖已流转的订单。
     */
    @Update("""
            UPDATE t_order
            SET refund_status = 2,
                refunded_amount = #{amount},
                updated_at = NOW(3)
            WHERE id = #{orderId}
              AND status = 60
            """)
    int markAfterSalesRefunded(@Param("orderId") Long orderId,
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
