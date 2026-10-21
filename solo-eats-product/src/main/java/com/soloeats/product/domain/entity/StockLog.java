package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_stock_log")
public class StockLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;

    private Long skuId;

    private String bizType;

    private Long bizId;

    private String op;

    private Integer qty;

    private Integer beforeAvailable;

    private Integer afterAvailable;

    private LocalDateTime createdAt;
}
