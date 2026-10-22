package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件发送日志。0 待发 / 1 成功 / 2 失败。
 */
@Data
@TableName("t_sys_mail_log")
public class SysMailLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String templateCode;

    private String toAddr;

    private String subject;

    private String content;

    private Integer status;

    private String errorMsg;

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;
}
