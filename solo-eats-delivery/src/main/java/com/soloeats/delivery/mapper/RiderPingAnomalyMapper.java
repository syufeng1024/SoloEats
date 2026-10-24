package com.soloeats.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.soloeats.delivery.domain.entity.RiderPingAnomaly;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RiderPingAnomalyMapper extends BaseMapper<RiderPingAnomaly> {

    /**
     * 已存在超时异常（type=1）的配送单 ID，供超时扫描去重，避免每分钟重复写。
     */
    @Select("""
            <script>
            SELECT DISTINCT delivery_id FROM t_rider_ping_anomaly
            WHERE type = 1 AND delivery_id IN
            <foreach collection="deliveryIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<Long> selectTimedOutDeliveryIds(@Param("deliveryIds") Collection<Long> deliveryIds);
}
