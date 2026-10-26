package com.soloeats.report.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 平台经营日报。金额单位统一为分，由 ReportAggregateJob 每日 02:30 预聚合。
 */
@Data
@TableName("t_report_daily_platform")
public class ReportDailyPlatform {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private LocalDate statDate;

    private Long gmv;

    private Integer orderCount;

    private Integer paidOrderCount;

    private Long refundAmount;

    private Integer refundOrderCount;

    private Integer newUserCount;

    private Integer activeUserCount;

    private Long avgOrderAmount;

    private Long commissionIncome;

    private Long settlementExpense;

    private Long subsidyCost;

    private Long grossProfit;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
