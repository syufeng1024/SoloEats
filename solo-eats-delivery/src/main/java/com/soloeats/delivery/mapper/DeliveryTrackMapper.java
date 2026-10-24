package com.soloeats.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.delivery.domain.entity.DeliveryTrack;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DeliveryTrackMapper extends BaseMapper<DeliveryTrack> {

    /**
     * 最近 limit 个轨迹点，按 report_at 升序返回。
     * 先按 report_at DESC 取尾段，再在外层升序，避免 ASC + LIMIT 取到最早的点。
     */
    @Select("""
            SELECT * FROM (
                SELECT * FROM t_delivery_track
                WHERE delivery_id = #{deliveryId}
                ORDER BY report_at DESC, id DESC
                LIMIT #{limit}
            ) recent
            ORDER BY recent.report_at ASC, recent.id ASC
            """)
    List<DeliveryTrack> selectByDelivery(@Param("deliveryId") Long deliveryId,
                                         @Param("limit") int limit);

    /** 后台回放：全量轨迹按 report_at 升序。 */
    @Select("""
            SELECT * FROM t_delivery_track
            WHERE delivery_id = #{deliveryId}
            ORDER BY report_at ASC, id ASC
            """)
    List<DeliveryTrack> selectAllByDelivery(@Param("deliveryId") Long deliveryId);

    @Select("""
            SELECT * FROM t_delivery_track
            WHERE delivery_id = #{deliveryId}
            ORDER BY report_at DESC, id DESC
            LIMIT 1
            """)
    DeliveryTrack selectLatestByDelivery(@Param("deliveryId") Long deliveryId);

    /**
     * 单次上报 ≤20 点，多行 INSERT 一次落库；tenant_id 由 TenantLineInnerInterceptor 自动补列。
     */
    @Insert("""
            <script>
            INSERT INTO t_delivery_track
                (delivery_id, rider_id, lng, lat, speed, accuracy_m, report_at)
            VALUES
            <foreach collection="items" item="item" separator=",">
                (#{item.deliveryId}, #{item.riderId}, #{item.lng}, #{item.lat},
                 #{item.speed}, #{item.accuracyM}, #{item.reportAt})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<DeliveryTrack> items);
}
