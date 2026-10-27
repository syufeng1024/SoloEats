package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.PointAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PointAccountMapper extends BaseMapper<PointAccount> {

    /**
     * 账户不存在则建一行余额 0。依赖 uk_user(user_id) 保证并发下只有一行。
     * tenant_id 由 TenantLineInnerInterceptor 注入，不手写。
     */
    @Insert("""
            INSERT INTO t_point_account (user_id, balance, total_earned, total_consumed)
            VALUES (#{userId}, 0, 0, 0)
            ON DUPLICATE KEY UPDATE id = id
            """)
    int ensureAccount(@Param("userId") Long userId);

    /**
     * 积分加减的唯一写入口：条件更新保证余额不会被扣成负数。
     *
     * <p>{@code WHERE balance + #{delta} >= 0} 与 SET 在同一条语句中完成，
     * 不做「先 SELECT 余额再 UPDATE」。返回 0 表示余额不足或账户不存在，
     * 扣减场景由调用方转 40206 POINT_NOT_ENOUGH。
     */
    @Update("""
            UPDATE t_point_account
            SET balance = balance + #{delta},
                total_earned = total_earned + IF(#{delta} > 0, #{delta}, 0),
                total_consumed = total_consumed + IF(#{delta} < 0, -#{delta}, 0),
                version = version + 1,
                updated_at = NOW(3)
            WHERE user_id = #{userId}
              AND balance + #{delta} >= 0
            """)
    int changeBalance(@Param("userId") Long userId, @Param("delta") int delta);
}
