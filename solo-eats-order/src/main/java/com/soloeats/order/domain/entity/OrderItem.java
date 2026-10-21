package com.soloeats.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long shopId;

    private Long spuId;

    private Long skuId;

    private String skuName;

    private String specJson;

    private String imageUrl;

    private Long originalUnitPrice;

    private Integer quantity;

    private Long originalAmount;

    private Long discountAmount;

    private Long merchantDiscount;

    private Long platformDiscount;

    private Long payableAmount;

    private Integer refundedQty;

    private Long refundedAmount;

    private Integer restockFlag;

    private LocalDateTime createdAt;
}
