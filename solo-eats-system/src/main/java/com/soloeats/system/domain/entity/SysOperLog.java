package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志。只增不改，因此没有 updated_at。
 */
@Data
@TableName("t_sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String traceId;

    private String module;

    private String action;

    private String method;

    private String requestUri;

    private String requestMethod;

    /** 脱敏并截断后的 JSON 摘要。 */
    private String requestParams;

    private Long operatorId;

    private String operatorName;

    private String operatorType;

    private String ip;

    private String ua;

    private Integer resultCode;

    private String errorMsg;

    private Integer costMs;

    private LocalDateTime createdAt;
}
