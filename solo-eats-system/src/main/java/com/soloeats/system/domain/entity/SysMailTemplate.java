package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件模板。subject/content 支持 {{key}} 占位符。
 */
@Data
@TableName("t_sys_mail_template")
public class SysMailTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String templateCode;

    private String templateName;

    private String subject;

    private String content;

    /** 0 启用 1 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
