package com.soloeats.app.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 骑手端「我的」只读查询。
 *
 * <p>直接查 t_rider / t_rider_online_log / t_rider_ping_anomaly 的少量展示字段，
 * 不引入 delivery 模块实体，避免 app 层被领域实体绑定。写入仍全部走 delivery 服务。
 */
@Mapper
public interface RiderSelfQueryMapper {

    @Select("""
            SELECT rating, on_time_rate, total_orders, cert_status
            FROM t_rider
            WHERE id = #{riderId} AND deleted = 0
            """)
    RiderStatsRow selectStats(@Param("riderId") Long riderId);

    /**
     * 只取 action = 1（上线）记录：同一天首末上线时间的差值作为在线时长估算。
     * 该口径不是精确在网时长——中途下线再上线会被算进连续区间，
     * 精确口径需要成对匹配上下线记录，现有表只记录动作，暂不支持。
     */
    @Select("""
            SELECT DATE(created_at) AS work_date,
                   COUNT(*) AS online_times,
                   TIMESTAMPDIFF(MINUTE, MIN(created_at), MAX(created_at)) AS online_minutes
            FROM t_rider_online_log
            WHERE rider_id = #{riderId}
              AND action = 1
              AND created_at >= #{from}
              AND created_at < #{to}
            GROUP BY DATE(created_at)
            ORDER BY work_date DESC
            """)
    List<RiderAttendanceRow> listAttendance(@Param("riderId") Long riderId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    @Select("SELECT COUNT(*) FROM t_rider_ping_anomaly WHERE rider_id = #{riderId}")
    long countAnomalies(@Param("riderId") Long riderId);

    @Select("""
            SELECT id, delivery_id, type, remark, images_json, created_at
            FROM t_rider_ping_anomaly
            WHERE rider_id = #{riderId}
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<RiderAnomalyRow> listAnomalies(@Param("riderId") Long riderId,
                                        @Param("limit") long limit,
                                        @Param("offset") long offset);
}
