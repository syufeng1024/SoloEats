package com.soloeats.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.product.domain.entity.SoldOutRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SoldOutRecordMapper extends BaseMapper<SoldOutRecord> {

    /**
     * 恢复单 SKU：只关闭仍未恢复的记录，重复恢复是幂等的。
     */
    @Update("""
            UPDATE t_sold_out_record
            SET restored_at = NOW(3)
            WHERE shop_id = #{shopId}
              AND sku_id = #{skuId}
              AND restored_at IS NULL
            """)
    int markRestored(@Param("shopId") Long shopId, @Param("skuId") Long skuId);

    @Update("""
            UPDATE t_sold_out_record
            SET restored_at = NOW(3)
            WHERE shop_id = #{shopId}
              AND spu_id = #{spuId}
              AND restored_at IS NULL
            """)
    int markRestoredBySpu(@Param("shopId") Long shopId, @Param("spuId") Long spuId);

    @Select("""
            SELECT reason, COUNT(*) AS count
            FROM t_sold_out_record
            WHERE shop_id = #{shopId}
              AND sold_out_at >= #{start}
              AND sold_out_at < #{end}
            GROUP BY reason
            ORDER BY COUNT(*) DESC, reason
            """)
    List<ReasonStatRow> selectReasonStats(@Param("shopId") Long shopId,
                                          @Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);
}
