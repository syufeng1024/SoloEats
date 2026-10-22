package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号-角色关联。表无 deleted 列：撤销授权即物理删除该行（uk_user_role 保证不重复）。
 */
@Data
@TableName("t_sys_user_role")
public class SysUserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private Long roleId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
