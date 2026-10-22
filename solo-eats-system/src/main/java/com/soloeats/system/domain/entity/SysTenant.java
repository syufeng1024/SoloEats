package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户。平台级表，不带 tenant_id，不参与行级租户过滤。
 */
@Data
@TableName("t_sys_tenant")
public class SysTenant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantCode;

    private String tenantName;

    /** 1 平台 2 商家 */
    private Integer tenantType;

    private Long merchantId;

    private Long packageId;

    /** 0 启用 1 停用 2 过期（2 由 expire_at 派生，不回写） */
    private Integer status;

    private LocalDateTime expireAt;

    private String contactName;

    private String contactPhone;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
