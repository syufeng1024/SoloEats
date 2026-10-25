package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 营销活动（满减/折扣）。阶段A-0 只使用 promotionType=1、bearer=1、scopeType=1 的平台满减。
 */
@Data
@TableName("t_promotion")
public class Promotion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 1满减 2折扣 3新人立减 */
    private Integer promotionType;

    private String promotionName;

    /** 1平台 2商家 */
    private Integer bearer;

    private Long merchantId;

    private Long shopId;

    /** 1全平台 2指定商家 3指定门店 4指定品类 5指定商品 */
    private Integer scopeType;

    private String scopeJson;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    /** 0不可与券叠加 1可叠加 */
    private Integer stackable;

    private Integer priority;

    /** 0草稿 1进行中 2已结束 3暂停 */
    private Integer status;

    private Long totalBudget;

    private Long usedBudget;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
