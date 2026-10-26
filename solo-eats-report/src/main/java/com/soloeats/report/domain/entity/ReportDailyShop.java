package com.soloeats.report.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门店经营日报。cancel_rate 为百分比数值（3.25 表示 3.25%）。
 */
@Data
@TableName("t_report_daily_shop")
public class ReportDailyShop {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private LocalDate statDate;

    private Long shopId;

    private Long merchantId;

    private Integer orderCount;

    private Long gmv;

    private Long refundAmount;

    private Integer avgPrepMinutes;

    private BigDecimal cancelRate;

    private BigDecimal rating;

    private Integer pickupOrderCount;

    private Integer deliveryOrderCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
