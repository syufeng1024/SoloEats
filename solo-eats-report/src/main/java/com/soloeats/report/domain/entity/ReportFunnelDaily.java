package com.soloeats.report.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 流量转化漏斗日报：首页曝光 → 点击 → 加购 → 提交订单 → 支付成功。
 */
@Data
@TableName("t_report_funnel_daily")
public class ReportFunnelDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private LocalDate statDate;

    private Integer exposeCount;

    private Integer clickCount;

    private Integer cartCount;

    private Integer orderCount;

    private Integer payCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
