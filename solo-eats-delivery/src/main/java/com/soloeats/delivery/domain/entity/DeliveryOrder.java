package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_delivery_order")
public class DeliveryOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deliveryNo;

    private Long orderId;

    private Long shopId;

    private Long riderId;

    private Integer status;

    private String pickupAddress;

    private BigDecimal pickupLng;

    private BigDecimal pickupLat;

    private String receiverAddress;

    private BigDecimal receiverLng;

    private BigDecimal receiverLat;

    private Integer distanceM;

    private Long deliveryFee;

    private Integer dispatchCount;

    private LocalDateTime dispatchExpireAt;

    private LocalDateTime acceptedAt;

    private LocalDateTime pickedUpAt;

    private LocalDateTime deliveredAt;

    private String cancelReason;

    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
