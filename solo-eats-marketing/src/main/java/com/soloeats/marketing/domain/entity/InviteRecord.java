package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邀请记录。uk_invitee 保证一个被邀请人只能绑定一次；
 * status：0已注册 1首单已完成 2无效。
 */
@Data
@TableName("t_invite_record")
public class InviteRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long inviterId;

    private Long inviteeId;

    private String inviteCode;

    private Integer status;

    /** 绑定成功时写入，结算时读取，避免结算规则变更影响历史记录 */
    private Integer rewardPoint;

    private Integer rewardGrowth;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
