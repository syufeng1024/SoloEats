package com.soloeats.app.support;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 异常订单池写入。t_order_exception 为既有表（无 tenant_id），
 * 由 order 域维护结构，delivery 域通过 OrderExceptionPort 间接写入。
 */
@Mapper
public interface OrderExceptionMapper {

    @Insert("""
            INSERT INTO t_order_exception
                (order_id, delivery_order_id, exception_type, source_type, source_id,
                 description, status, created_at)
            VALUES
                (#{orderId}, #{deliveryOrderId}, #{exceptionType}, #{sourceType}, #{sourceId},
                 #{description}, 0, NOW(3))
            """)
    int insert(@Param("orderId") Long orderId,
               @Param("deliveryOrderId") Long deliveryOrderId,
               @Param("exceptionType") Integer exceptionType,
               @Param("sourceType") Integer sourceType,
               @Param("sourceId") Long sourceId,
               @Param("description") String description);
}
