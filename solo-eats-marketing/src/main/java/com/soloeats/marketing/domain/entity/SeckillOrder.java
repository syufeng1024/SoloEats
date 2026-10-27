package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀预占单。uk_user_item(user_id, item_id) 兜底每人每场限购：
 * 释放后复用同一条记录（status 2 → 0），不重复 INSERT。
 */
@Data
@TableName("t_seckill_order")
public class SeckillOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long activityId;

    private Long itemId;

    private Long userId;

    /** 下单链路回写，本卡不涉及 order 模块，保持 NULL */
    private Long orderId;

    private Integer qty;

    /** 0预占 1已支付 2已释放 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
