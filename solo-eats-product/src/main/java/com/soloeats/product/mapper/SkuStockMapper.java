package com.soloeats.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.product.domain.entity.SkuStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SkuStockMapper extends BaseMapper<SkuStock> {

    @Update("""
            UPDATE t_sku_stock
            SET available_qty = available_qty - #{qty},
                locked_qty = locked_qty + #{qty},
                version = version + 1,
                updated_at = NOW(3)
            WHERE shop_id = #{shopId}
              AND sku_id = #{skuId}
              AND sold_out = 0
              AND available_qty >= #{qty}
            """)
    int lockStock(@Param("shopId") Long shopId,
                  @Param("skuId") Long skuId,
                  @Param("qty") int qty);

    @Update("""
            UPDATE t_sku_stock
            SET locked_qty = locked_qty - #{qty},
                sold_qty = sold_qty + #{qty},
                version = version + 1,
                updated_at = NOW(3)
            WHERE shop_id = #{shopId}
              AND sku_id = #{skuId}
              AND locked_qty >= #{qty}
            """)
    int confirmStock(@Param("shopId") Long shopId,
                     @Param("skuId") Long skuId,
                     @Param("qty") int qty);

    @Update("""
            UPDATE t_sku_stock
            SET available_qty = available_qty + #{qty},
                locked_qty = locked_qty - #{qty},
                version = version + 1,
                updated_at = NOW(3)
            WHERE shop_id = #{shopId}
              AND sku_id = #{skuId}
              AND locked_qty >= #{qty}
            """)
    int releaseStock(@Param("shopId") Long shopId,
                     @Param("skuId") Long skuId,
                     @Param("qty") int qty);

    @Update("""
            UPDATE t_sku_stock
            SET sold_qty = sold_qty - #{qty},
                available_qty = available_qty + #{qty},
                version = version + 1,
                updated_at = NOW(3)
            WHERE shop_id = #{shopId}
              AND sku_id = #{skuId}
              AND sold_qty >= #{qty}
            """)
    int refundStock(@Param("shopId") Long shopId,
                    @Param("skuId") Long skuId,
                    @Param("qty") int qty);

    /**
     * 商家维护可售库存与估清标记；null 字段不更新。
     */
    @Update("""
            <script>
            UPDATE t_sku_stock
            SET version = version + 1,
                updated_at = NOW(3)
                <if test="availableQty != null">, available_qty = #{availableQty}</if>
                <if test="soldOut != null">, sold_out = #{soldOut}</if>
            WHERE shop_id = #{shopId}
              AND sku_id = #{skuId}
            </script>
            """)
    int updateManualStock(@Param("shopId") Long shopId,
                          @Param("skuId") Long skuId,
                          @Param("availableQty") Integer availableQty,
                          @Param("soldOut") Boolean soldOut);
}
