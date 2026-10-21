package com.soloeats.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.marketing.domain.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Update("""
            UPDATE t_user_coupon
            SET status = 1,
                order_id = #{orderId},
                locked_at = NOW(3),
                version = version + 1
            WHERE id = #{couponId}
              AND user_id = #{userId}
              AND status = 0
              AND expire_at > NOW(3)
            """)
    int lock(@Param("couponId") Long couponId,
             @Param("userId") Long userId,
             @Param("orderId") Long orderId);

    @Update("""
            UPDATE t_user_coupon
            SET status = 0,
                order_id = NULL,
                locked_at = NULL,
                version = version + 1
            WHERE id = #{couponId}
              AND order_id = #{orderId}
              AND status = 1
            """)
    int release(@Param("couponId") Long couponId,
                @Param("orderId") Long orderId);

    @Update("""
            UPDATE t_user_coupon
            SET status = 2,
                used_at = NOW(3),
                version = version + 1
            WHERE id = #{couponId}
              AND order_id = #{orderId}
              AND status = 1
            """)
    int consume(@Param("couponId") Long couponId,
                @Param("orderId") Long orderId);

    @Update("""
            UPDATE t_user_coupon
            SET status = 0,
                order_id = NULL,
                used_at = NULL,
                version = version + 1
            WHERE id = #{couponId}
              AND order_id = #{orderId}
              AND status = 2
              AND expire_at > NOW(3)
            """)
    int rollbackValid(@Param("couponId") Long couponId,
                      @Param("orderId") Long orderId);

    @Update("""
            UPDATE t_user_coupon
            SET status = 5,
                order_id = NULL,
                version = version + 1
            WHERE id = #{couponId}
              AND order_id = #{orderId}
              AND status = 2
              AND expire_at <= NOW(3)
            """)
    int rollbackExpired(@Param("couponId") Long couponId,
                        @Param("orderId") Long orderId);
}
