package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 骑手配送费规则。rider_id 为 null 表示全局费率，骑手专属优先。
 */
@Data
@TableName("t_rider_fee_rule")
public class RiderFeeRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long riderId;

    private Long baseFee;

    private Long perKmFee;

    private Long peakDelta;

    private Long badWeatherDelta;

    private Long timeoutPenalty;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    /** 0启用 1停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
