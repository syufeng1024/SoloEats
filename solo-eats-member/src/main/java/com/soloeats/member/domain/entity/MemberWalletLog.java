package com.soloeats.member.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 钱包流水。每次余额变动必须在同一事务写一条，金额恒为正数，方向由 direction 区分。
 * uk_biz(biz_type, biz_id, request_id) 是幂等兜底，request_id 为空时 MySQL 允许多行并存。
 */
@Data
@TableName("t_member_wallet_log")
public class MemberWalletLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    /** RECHARGE / CONSUME / REFUND / ADJUST / WITHDRAW / FREEZE / UNFREEZE */
    private String bizType;

    /**
     * 业务单 ID。充值、调账没有业务单，复用 user_id 使 uk_biz 按用户隔离；
     * 消费等场景传真实订单 ID。
     */
    private Long bizId;

    /** 幂等键，可为空；为空时不做去重 */
    private String requestId;

    /** 1 收入 2 支出 */
    private Integer direction;

    /** 变动金额，分，恒为正 */
    private Long amount;

    private Long beforeBalance;

    private Long afterBalance;

    /** 调账记为「操作人：原因」 */
    private String remark;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
