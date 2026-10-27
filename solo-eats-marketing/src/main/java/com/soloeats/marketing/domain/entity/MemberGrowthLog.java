package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 成长值流水。与积分账户完全分离：本表不参与任何积分加减或过期。
 *
 * <p>等级判定只统计 {@code effective_at >= now - 365d} 的流水合计，
 * {@code expire_at} 仅用于审计和后续清理，不参与实时计算。
 */
@Data
@TableName("t_member_growth_log")
public class MemberGrowthLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** t_member_user.id */
    private Long userId;

    /** 1消费 2签到 3评价 4邀请 5手工调整 */
    private Integer source;

    private Long bizId;

    /** 成长值，可负（手工调整） */
    private Integer growth;

    private Integer beforeValue;

    private Integer afterValue;

    private LocalDateTime effectiveAt;

    private LocalDateTime expireAt;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
