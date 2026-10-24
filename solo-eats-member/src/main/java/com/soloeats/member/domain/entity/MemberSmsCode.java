package com.soloeats.member.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信验证码。只保存手机号哈希，不保存明文；同一验证码只允许使用一次。
 */
@Data
@TableName("t_member_sms_code")
public class MemberSmsCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private String phoneHash;

    private String code;

    /** 1 登录 2 入驻 3 改绑 */
    private Integer scene;

    private LocalDateTime expireAt;

    /** 0 未用 1 已用 */
    private Integer used;

    private String ip;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
