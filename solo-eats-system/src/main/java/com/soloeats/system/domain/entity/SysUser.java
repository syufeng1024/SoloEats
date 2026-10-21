package com.soloeats.system.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台 / 商家 / 骑手账号。C 端消费者不在本表（见 T06 的 t_member_user）。
 */
@Data
@TableName("t_sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String username;

    private String passwordHash;

    private String nickname;

    private String phone;

    private String email;

    private String avatarUrl;

    /** 1 平台 2 商家 3 骑手 */
    private Integer userType;

    private Long merchantId;

    private Long shopId;

    private Long riderId;

    private Long deptId;

    private Long postId;

    /** 1 全部 2 本租户 3 本商家 4 本门店 5 本人 */
    private Integer dataScope;

    /** 0 启用 1 停用 */
    private Integer status;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
