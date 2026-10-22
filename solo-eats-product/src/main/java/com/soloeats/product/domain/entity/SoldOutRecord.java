package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 估清记录。只增不删；restored_at 为空表示仍处于估清。
 */
@Data
@TableName("t_sold_out_record")
public class SoldOutRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long shopId;

    private Long skuId;

    private Long spuId;

    private String reason;

    private LocalDateTime soldOutAt;

    private LocalDateTime restoredAt;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime createdAt;
}
