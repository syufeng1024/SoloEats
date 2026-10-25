package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动档位。thresholdAmount 为门槛（分），同一活动命中时取满足条件的最大门槛档。
 */
@Data
@TableName("t_promotion_rule")
public class PromotionRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long promotionId;

    private Long thresholdAmount;

    /** 满减金额，分；折扣活动为 NULL */
    private Long discountAmount;

    /** 折扣率，如 0.85；满减活动为 NULL */
    private BigDecimal discountRate;

    /** 折扣封顶，分 */
    private Long maxDiscount;

    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
