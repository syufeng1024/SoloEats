package com.soloeats.member.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 隐私协议确认记录。每次确认或撤回都插新行，历史只增不改，
 * 以 {@code (userId, agreementVersion)} 最新一条作为当前状态。
 */
@Data
@TableName("t_member_agreement_confirm")
public class MemberAgreementConfirm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    private String agreementVersion;

    /** 1 同意 0 撤回 */
    private Integer agree;

    private LocalDateTime confirmedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
