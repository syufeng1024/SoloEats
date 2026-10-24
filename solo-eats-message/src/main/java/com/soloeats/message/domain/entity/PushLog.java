package com.soloeats.message.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 下发日志。消息开关关闭记 status=0（跳过），WS 离线记 status=2 + error_msg=offline，
 * 两种情况都不影响站内信落库。
 */
@Data
@TableName("t_push_log")
public class PushLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 关联站内信ID；纯 WS 通知为空 */
    private Long messageId;

    /** WS / SITE / PUSH */
    private String channel;

    private Integer receiverType;

    private Long receiverId;

    /** 0待发 1成功 2失败 */
    private Integer status;

    private String errorMsg;

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;
}
