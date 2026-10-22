package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * SMTP 邮件账号。passwordCipher 为 AES-GCM 密文，禁止明文落库。
 */
@Data
@TableName("t_sys_mail_account")
public class SysMailAccount {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String accountName;

    private String host;

    private Integer port;

    private String username;

    private String passwordCipher;

    private String fromAddr;

    /** 0 明文/STARTTLS 1 SSL。列名 ssl 是 MySQL 保留字，落库改用 ssl_enabled。 */
    @TableField("ssl_enabled")
    private Integer ssl;

    /** 0 启用 1 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
