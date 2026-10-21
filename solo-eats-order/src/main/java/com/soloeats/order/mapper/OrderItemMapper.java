package com.soloeats.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.order.domain.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Update("""
            UPDATE t_order_item
            SET refunded_qty = quantity,
                refunded_amount = payable_amount
            WHERE order_id = #{orderId}
            """)
    int markAllRefunded(@Param("orderId") Long orderId);
}
