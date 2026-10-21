package com.soloeats.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_payment_order")
public class PaymentOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String paymentNo;

    private Long orderId;

    private Integer channel;

    private Long amount;

    private Integer status;

    private String channelTradeNo;

    private String idempotencyKey;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
