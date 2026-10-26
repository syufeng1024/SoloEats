package com.soloeats.service.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单流转记录，只增不改（审计流水）。
 */
@Data
@TableName("t_ticket_record")
public class TicketRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long ticketId;

    /** 创建记录时为空 */
    private Integer fromStatus;

    private Integer toStatus;

    private String action;

    private Long operatorId;

    private String operatorName;

    private String content;

    private LocalDateTime createdAt;
}
