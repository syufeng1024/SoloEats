package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动适用范围。refType：1商家 2门店 3品类 4SPU 5SKU。
 * 阶段A-0 只支持全平台（scopeType=1），本实体供阶段A 扩展使用。
 */
@Data
@TableName("t_promotion_scope")
public class PromotionScope {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long promotionId;

    private Integer refType;

    private Long refId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
