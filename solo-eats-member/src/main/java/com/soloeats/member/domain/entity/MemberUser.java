package com.soloeats.member.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * C 端消费者账号。与 t_sys_user 严格分离：平台/商家/骑手在 t_sys_user，
 * 消费者只在本表，靠 token 的 roles=C_USER 区分。
 */
@Data
@TableName("t_member_user")
public class MemberUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** AES-GCM 密文，仅内部服务解密使用 */
    private String phoneCipher;

    /** SHA-256(手机号 + 盐) 小写 hex，唯一键与查询用 */
    private String phoneHash;

    /** 展示用脱敏手机号 */
    private String phoneMask;

    private String nickname;

    private String avatarUrl;

    /** 0 未知 1 男 2 女 */
    private Integer gender;

    private LocalDate birthday;

    /** 0 正常 1 禁用 2 已注销/拉黑 */
    private Integer status;

    /** 1 手机号 2 后台导入 */
    private Integer registerSource;

    private Long memberLevelId;

    private Integer growthValue;

    private Integer orderCount;

    /** 累计消费，分 */
    private Long totalAmount;

    private LocalDateTime lastOrderAt;

    private LocalDateTime lastLoginAt;

    private LocalDateTime cancelApplyAt;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
