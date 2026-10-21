package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_sys_menu")
public class SysMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long parentId;

    private String menuName;

    /** 1 目录 2 菜单 3 按钮 */
    private Integer menuType;

    private String path;

    private String component;

    /** 权限标识，如 system:user:create */
    private String perms;

    private String icon;

    private Integer sort;

    /** 0 显示 1 隐藏 */
    private Integer visible;

    /** 0 启用 1 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
