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
}
