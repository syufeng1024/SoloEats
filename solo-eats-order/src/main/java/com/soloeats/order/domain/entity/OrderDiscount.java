package com.soloeats.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_order_discount")
public class OrderDiscount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Integer discountType;

    private Long sourceId;

    private Integer bearer;

    private Long amount;

    private String apportionJson;

    private LocalDateTime createdAt;
}
