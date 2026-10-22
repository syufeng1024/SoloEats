package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志。成功与失败都落库，失败时 user_id 可为空。
 */
@Data
@TableName("t_sys_login_log")
public class SysLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private String username;

    /** 1 密码 2 验证码 3 mock */
    private Integer loginType;

    /** 1 成功 0 失败 */
    private Integer success;

    private String failReason;

    private String ip;

    private String ua;

    private LocalDateTime createdAt;
}
