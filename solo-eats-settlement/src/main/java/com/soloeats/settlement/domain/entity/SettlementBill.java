package com.soloeats.settlement.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_settlement_bill")
public class SettlementBill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String billNo;

    private Long merchantId;

    private Long shopId;

    private LocalDate billDate;

    private LocalDateTime periodStart;

    private LocalDateTime periodEnd;

    private Long grossAmount;

    private Long merchantDiscount;

    private Long platformDiscount;

    private Long commissionBase;

    private Long commissionAmount;

    private Long performanceFee;

    private Long refundAmount;

    private Long carryForwardAmount;

    private Long payableAmount;

    private Integer status;

    private Long reviewedBy;

    private LocalDateTime reviewedAt;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
