package com.soloeats.delivery.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_rider_income")
public class RiderIncome {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long riderId;

    private Long deliveryOrderId;

    private Long orderId;

    private Long baseFee;

    private Long distanceFee;

    private Long timeSubsidy;

    private Long weatherSubsidy;

    private Long tipAmount;

    private Long penaltyAmount;

    private Long compensationAmount;

    private Long totalAmount;

    private LocalDate settlementDate;

    private Integer status;

    private LocalDateTime createdAt;
}
