package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.SeckillOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {

    /**
     * 按 (user_id, item_id) 取唯一预占记录，命中 uk_user_item。
     * tenant_id 由 TenantLineInnerInterceptor 统一追加。
     */
    @Select("""
            SELECT *
            FROM t_seckill_order
            WHERE user_id = #{userId} AND item_id = #{itemId}
            LIMIT 1
            """)
    SeckillOrder selectByUserAndItem(@Param("userId") Long userId, @Param("itemId") Long itemId);

    /**
     * 释放：条件更新 status 0 → 2，返回 0 表示已被并发释放。
     */
    @Update("""
            UPDATE t_seckill_order
            SET status = 2, updated_at = NOW(3)
            WHERE id = #{id} AND status = 0
            """)
    int release(@Param("id") Long id);

    /**
     * 复用释放过的旧记录重新预占（status 2 → 0），避免撞 uk_user_item。
     * 返回 0 表示已被并发重新预占。
     */
    @Update("""
            UPDATE t_seckill_order
            SET status = 0, qty = #{qty}, order_id = NULL, created_at = NOW(3), updated_at = NOW(3)
            WHERE id = #{id} AND status = 2
            """)
    int reactivate(@Param("id") Long id, @Param("qty") int qty);
}
