package com.soloeats.report.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商品销售日报。金额单位为分；sale_qty 为订单明细购买数量，refund_qty 单独记录已退数量。
 */
@Data
@TableName("t_report_daily_spu")
public class ReportDailySpu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private LocalDate statDate;

    private Long shopId;

    private Long spuId;

    private Long categoryId;

    private Integer saleQty;

    private Long saleAmount;

    private Integer refundQty;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
