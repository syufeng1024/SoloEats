package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门。ancestors 为祖级路径（如 0,1,3），移动部门时必须级联更新子部门。
 */
@Data
@TableName("t_sys_dept")
public class SysDept {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long parentId;

    /** 祖级路径，根部门为 "0" */
    private String ancestors;

    private String deptName;

    private String leader;

    private String phone;

    private Integer sort;

    /** 0 启用 1 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
