package com.soloeats.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.product.domain.entity.StockLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface StockLockMapper extends BaseMapper<StockLock> {

    @Update("""
            UPDATE t_stock_lock
            SET status = #{toStatus},
                updated_at = NOW(3)
            WHERE order_id = #{orderId}
              AND sku_id = #{skuId}
              AND status = #{fromStatus}
            """)
    int updateStatus(@Param("orderId") Long orderId,
                     @Param("skuId") Long skuId,
                     @Param("fromStatus") int fromStatus,
                     @Param("toStatus") int toStatus);
}
