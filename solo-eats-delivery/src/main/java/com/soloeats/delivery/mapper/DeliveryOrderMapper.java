package com.soloeats.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.delivery.domain.entity.DeliveryOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface DeliveryOrderMapper extends BaseMapper<DeliveryOrder> {

    @Select("SELECT * FROM t_delivery_order WHERE order_id = #{orderId} LIMIT 1")
    DeliveryOrder findByOrderId(@Param("orderId") Long orderId);

    @Update("""
            UPDATE t_delivery_order
            SET status = 10,
                dispatch_count = dispatch_count + 1,
                dispatch_expire_at = #{expireAt},
                version = version + 1,
                updated_at = NOW(3)
            WHERE order_id = #{orderId}
              AND rider_id IS NULL
              AND status IN (0, 10, 71)
            """)
    int dispatch(@Param("orderId") Long orderId,
                 @Param("expireAt") LocalDateTime expireAt);

    @Update("""
            UPDATE t_delivery_order
            SET rider_id = #{riderId},
                status = 20,
                accepted_at = NOW(3),
                version = version + 1,
                updated_at = NOW(3)
            WHERE order_id = #{orderId}
              AND status IN (0, 10)
              AND rider_id IS NULL
            """)
    int accept(@Param("orderId") Long orderId,
               @Param("riderId") Long riderId);

    @Update("""
            UPDATE t_delivery_order
            SET status = 40,
                picked_up_at = NOW(3),
                version = version + 1,
                updated_at = NOW(3)
            WHERE order_id = #{orderId}
              AND rider_id = #{riderId}
              AND status = 20
            """)
    int pickup(@Param("orderId") Long orderId,
               @Param("riderId") Long riderId);

    @Update("""
            UPDATE t_delivery_order
            SET status = 50,
                delivered_at = NOW(3),
                version = version + 1,
                updated_at = NOW(3)
            WHERE order_id = #{orderId}
              AND rider_id = #{riderId}
              AND status = 40
            """)
    int delivered(@Param("orderId") Long orderId,
                  @Param("riderId") Long riderId);

    /**
     * 拒单只累加派单次数；重试耗尽时释放骑手并退回待派单（10 -> 0）。
     * 条件里限定 status = 10，避免覆盖其他骑手的接单结果。
     */
    @Update("""
            UPDATE t_delivery_order
            SET dispatch_count = dispatch_count + 1,
                rider_id = CASE WHEN #{releaseToHall} THEN NULL ELSE rider_id END,
                status = CASE WHEN #{releaseToHall} THEN 0 ELSE status END,
                dispatch_expire_at = CASE WHEN #{releaseToHall} THEN NULL ELSE dispatch_expire_at END,
                version = version + 1,
                updated_at = NOW(3)
            WHERE order_id = #{orderId}
              AND status = 10
              AND (rider_id = #{riderId} OR rider_id IS NULL)
            """)
    int reject(@Param("orderId") Long orderId,
               @Param("riderId") Long riderId,
               @Param("releaseToHall") boolean releaseToHall);

    /**
     * 接单后回填承诺送达时间。只写 promise_deliver_at，不改状态。
     */
    @Update("""
            UPDATE t_delivery_order
            SET promise_deliver_at = #{promiseDeliverAt},
                version = version + 1,
                updated_at = NOW(3)
            WHERE id = #{id}
            """)
    int updatePromiseDeliverAt(@Param("id") Long id,
                               @Param("promiseDeliverAt") LocalDateTime promiseDeliverAt);

    /**
     * 改派释放：rider_id 置空、退回待派单、派单次数与倒计时清零。
     * 条件限定原骑手与履约中状态，避免并发覆盖新骑手的接单结果。
     */
    @Update("""
            UPDATE t_delivery_order
            SET rider_id = NULL,
                status = 0,
                dispatch_count = 0,
                dispatch_expire_at = NULL,
                promise_deliver_at = NULL,
                version = version + 1,
                updated_at = NOW(3)
            WHERE order_id = #{orderId}
              AND rider_id = #{riderId}
              AND status IN (10, 20, 30, 40)
            """)
    int releaseForReassign(@Param("orderId") Long orderId,
                           @Param("riderId") Long riderId);
}
