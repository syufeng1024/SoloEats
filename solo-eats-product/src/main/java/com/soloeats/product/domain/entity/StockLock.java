package com.soloeats.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_stock_lock")
public class StockLock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long shopId;

    private Long skuId;

    private Integer qty;

    private Integer status;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
