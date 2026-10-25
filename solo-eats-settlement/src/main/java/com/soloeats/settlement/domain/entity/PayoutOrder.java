package com.soloeats.settlement.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 提现单。subject_type 1商家 2骑手；subject_id 对应 t_merchant.id / t_rider.id。
 */
@Data
@TableName("t_payout_order")
public class PayoutOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String payoutNo;

    private Integer subjectType;

    private Long subjectId;

    private Long billId;

    private Long amount;

    private Integer channel;

    private Integer status;

    private String failReason;

    private Long auditBy;

    private LocalDateTime auditAt;

    private String auditRemark;

    private LocalDateTime paidAt;

    private String accountSnapshotJson;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
