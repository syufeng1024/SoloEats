package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String roleCode;

    private String roleName;

    /** 1 平台 2 商家 3 骑手 */
    private Integer roleType;

    /** 1 全部 2 本租户 3 本商家 4 本门店 5 本人 */
    private Integer dataScope;

    private Integer sort;

    /** 0 启用 1 停用 */
    private Integer status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
