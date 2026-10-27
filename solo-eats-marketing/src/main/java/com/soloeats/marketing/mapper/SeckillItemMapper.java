package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.SeckillItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SeckillItemMapper extends BaseMapper<SeckillItem> {

    /**
     * 原子预扣：sold_qty + qty 不超过总库存才更新，返回 0 表示已售罄。
     * 这是防超卖的最后一道防线，Redis 预扣只用于拦截无效流量。
     */
    @Update("UPDATE t_seckill_item SET sold_qty = sold_qty + #{qty}, updated_at = NOW(3) "
            + "WHERE id = #{itemId} AND sold_qty + #{qty} <= seckill_stock")
    int increaseSoldQty(@Param("itemId") Long itemId, @Param("qty") int qty);

    /**
     * 释放回补：sold_qty 不小于 qty 才扣减，返回 0 表示 DB 已不一致（需告警排查）。
     */
    @Update("UPDATE t_seckill_item SET sold_qty = sold_qty - #{qty}, updated_at = NOW(3) "
            + "WHERE id = #{itemId} AND sold_qty >= #{qty}")
    int decreaseSoldQty(@Param("itemId") Long itemId, @Param("qty") int qty);

    @Select("""
            SELECT *
            FROM t_seckill_item
            WHERE activity_id = #{activityId}
            ORDER BY id ASC
            """)
    List<SeckillItem> selectByActivityId(@Param("activityId") Long activityId);

    /**
     * 批量取多个场次的商品，供 C 端场次列表一次查询，避免逐场次查询。
     */
    @Select("""
            <script>
            SELECT *
            FROM t_seckill_item
            WHERE activity_id IN
            <foreach collection="activityIds" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
            ORDER BY activity_id ASC, id ASC
            </script>
            """)
    List<SeckillItem> selectByActivityIds(@Param("activityIds") List<Long> activityIds);
}
