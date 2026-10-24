package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配送轨迹点（t_delivery_track，V15）。
 * report_at 为设备上报时间，允许乱序，查询侧统一按 report_at ASC 重排。
 */
@Data
@TableName("t_delivery_track")
public class DeliveryTrack {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long deliveryId;

    private Long riderId;

    private BigDecimal lng;

    private BigDecimal lat;

    /** 瞬时速度 m/s。 */
    private BigDecimal speed;

    /** 定位精度（米）。 */
    private BigDecimal accuracyM;

    private LocalDateTime reportAt;

    private LocalDateTime createdAt;
}
