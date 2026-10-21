package com.soloeats.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_refund_item")
public class RefundItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long refundId;

    private Long orderItemId;

    private Long skuId;

    private Integer qty;

    private Long refundAmount;

    private Integer restockFlag;

    private LocalDateTime createdAt;
}
