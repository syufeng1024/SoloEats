package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色-菜单关联。表无 deleted 列：全量替换授权时先删后插（uk_role_menu 保证不重复）。
 */
@Data
@TableName("t_sys_role_menu")
public class SysRoleMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long roleId;

    private Long menuId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
