package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户红包。status：0未用 1已用 2过期；金额单位分。
 *
 * <p>表上没有 (packet_id, user_id) 唯一键，抢取时通过红包主表行锁
 * 串行化同一红包，再检查该用户是否已领取。
 */
@Data
@TableName("t_user_red_packet")
public class UserRedPacket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long packetId;

    private Long userId;

    /** 抢到的金额，分 */
    private Long amount;

    private Integer status;

    private Long orderId;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
