package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 骑手异常上报（t_rider_ping_anomaly，V15）。
 * type：1超时 2联系不上用户 3地址错误 4餐品破损 5商家出餐慢 6用户拒收。
 */
@Data
@TableName("t_rider_ping_anomaly")
public class RiderPingAnomaly {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long riderId;

    private Long deliveryId;

    private Integer type;

    private String remark;

    /** 现场照片文件 ID / URL 的 JSON 数组。 */
    private String imagesJson;

    private LocalDateTime createdAt;
}
