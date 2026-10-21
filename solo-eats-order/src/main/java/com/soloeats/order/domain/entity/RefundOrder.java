package com.soloeats.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_refund_order")
public class RefundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String refundNo;

    private Long orderId;

    private Integer refundType;

    private Long amount;

    private String reason;

    private String evidenceJson;

    private Integer status;

    private String channelRefundNo;

    private String idempotencyKey;

    private LocalDateTime merchantReviewAt;

    private LocalDateTime successAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
