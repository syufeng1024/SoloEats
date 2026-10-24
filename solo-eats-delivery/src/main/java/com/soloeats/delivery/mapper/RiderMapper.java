package com.soloeats.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.delivery.domain.entity.Rider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RiderMapper extends BaseMapper<Rider> {

    @Select("SELECT MAX(rider_no) FROM t_rider WHERE deleted = 0")
    String findMaxRiderNo();

    @Update("""
            UPDATE t_rider
            SET online_status = #{onlineStatus},
                version = version + 1,
                updated_at = NOW(3)
            WHERE id = #{riderId}
              AND deleted = 0
            """)
    int updateOnlineStatus(@Param("riderId") Long id,
                           @Param("onlineStatus") Integer status);

    /**
     * 重新提交认证：cert_remark 必须显式置 NULL，MyBatis-Plus updateById 会忽略 null 字段。
     * cert_status &lt;&gt; 2 保证已通过的档案不能被覆盖。
     */
    @Update("""
            UPDATE t_rider
            SET real_name = #{realName},
                id_card_cipher = #{idCardCipher},
                id_card_mask = #{idCardMask},
                vehicle_type = #{vehicleType},
                cert_status = 1,
                cert_remark = NULL,
                health_cert_expire_at = #{healthCertExpireAt},
                version = version + 1,
                updated_at = NOW(3)
            WHERE id = #{riderId}
              AND deleted = 0
              AND cert_status <> 2
            """)
    int updateCertSubmission(@Param("riderId") Long riderId,
                             @Param("realName") String realName,
                             @Param("idCardCipher") String idCardCipher,
                             @Param("idCardMask") String idCardMask,
                             @Param("vehicleType") Integer vehicleType,
                             @Param("healthCertExpireAt") java.time.LocalDate healthCertExpireAt);

    @Update("""
            UPDATE t_rider
            SET online_status = #{onlineStatus},
                last_online_at = NOW(3),
                version = version + 1,
                updated_at = NOW(3)
            WHERE id = #{riderId}
              AND deleted = 0
            """)
    int updateOnlineStatusAndLastOnline(@Param("riderId") Long id,
                                        @Param("onlineStatus") Integer status);

    @Update("""
            UPDATE t_rider
            SET status = #{status},
                version = version + 1,
                updated_at = NOW(3)
            WHERE id = #{riderId}
              AND deleted = 0
            """)
    int updateStatus(@Param("riderId") Long riderId,
                     @Param("status") Integer status);

    @Update("""
            UPDATE t_rider
            SET max_concurrent_orders = #{maxConcurrentOrders},
                pickup_radius_m = #{pickupRadiusM},
                version = version + 1,
                updated_at = NOW(3)
            WHERE id = #{riderId}
              AND deleted = 0
            """)
    int updateLimits(@Param("riderId") Long riderId,
                     @Param("maxConcurrentOrders") Integer maxConcurrentOrders,
                     @Param("pickupRadiusM") Integer pickupRadiusM);
}
