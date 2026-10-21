package com.soloeats.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long merchantId;

    private Long shopId;

    private Integer fulfillmentType;

    private Integer status;

    private Integer paymentStatus;

    private Integer refundStatus;

    private Integer exceptionType;

    private Integer settlementStatus;

    private Long goodsAmount;

    private Long packagingFee;

    private Long deliveryFee;

    private Long discountAmount;

    private Long platformDiscount;

    private Long merchantDiscount;

    private Long payAmount;

    private Long refundedAmount;

    private Long addressId;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private BigDecimal receiverLng;

    private BigDecimal receiverLat;

    private String pickupPhone;

    private String pickupCode;

    private LocalDate pickupCodeDate;

    private Integer pickupCodeStatus;

    private LocalDateTime pickupReadyAt;

    private LocalDateTime pickedUpAt;

    private LocalDateTime pickupExpireAt;

    private LocalDateTime expectedDeliveryAt;

    private LocalDateTime completedAt;

    private LocalDateTime settleEligibleAt;

    private LocalDateTime cancelledAt;

    private String cancelReason;

    private String remark;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
