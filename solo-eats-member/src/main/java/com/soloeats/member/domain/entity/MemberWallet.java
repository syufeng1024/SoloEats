package com.soloeats.member.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * C 端钱包，与用户 1:1。余额变动必须走条件更新 + 流水，禁止先查再改。
 */
@Data
@TableName("t_member_wallet")
public class MemberWallet {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    /** 可用余额，分 */
    private Long balance;

    /** 冻结金额，分 */
    private Long frozenAmount;

    /** 累计充值，分 */
    private Long totalRecharge;

    /** 累计消费，分 */
    private Long totalConsume;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
