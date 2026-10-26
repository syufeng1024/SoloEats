package com.soloeats.service.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投诉单。封禁/扣款只登记 punish_action，实际执行由其他模块端口落地。
 */
@Data
@TableName("t_complaint")
public class Complaint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long ticketId;

    private Long orderId;

    private Integer complainantType;

    private Long complainantId;

    /** 2商家 3骑手 4平台 */
    private Integer targetType;

    private Long targetId;

    private String reason;

    /** 1商家 2骑手 3平台 4用户 */
    private Integer liability;

    private String result;

    /** 0无 1警告 2扣款 3封禁 */
    private Integer punishAction;

    private LocalDateTime callbackAt;

    private String callbackResult;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
