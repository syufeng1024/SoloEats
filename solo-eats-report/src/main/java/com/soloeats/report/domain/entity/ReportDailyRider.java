package com.soloeats.report.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 骑手配送日报。on_time_rate 为百分比数值（如 66.67 表示 66.67%）。
 */
@Data
@TableName("t_report_daily_rider")
public class ReportDailyRider {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private LocalDate statDate;

    private Long riderId;

    private Integer deliveryCount;

    private Integer onTimeCount;

    private BigDecimal onTimeRate;

    private Integer onlineMinutes;

    private Long incomeAmount;

    private Long penaltyAmount;

    private Integer avgDeliveryMinutes;

    private BigDecimal rating;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
