package com.soloeats.settlement.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_settlement_item")
public class SettlementItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long billId;

    private Long orderId;

    private Integer itemType;

    private Long sourceId;

    private Long amount;

    private String remark;

    private LocalDateTime createdAt;
}
