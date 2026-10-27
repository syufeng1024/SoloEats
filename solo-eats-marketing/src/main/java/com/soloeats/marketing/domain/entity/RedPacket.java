package com.soloeats.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 红包活动。金额单位分。
 *
 * <p>表里只有 remain_qty，没有 remain_amount；每次抢取时用
 * {@code total_amount - SUM(t_user_red_packet.amount)} 派生剩余金额。
 */
@Data
@TableName("t_red_packet")
public class RedPacket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 1拼手气 2新人 3分享 */
    private Integer packetType;

    private Long totalAmount;

    private Integer totalQty;

    private Integer remainQty;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    /** 0未开始 1进行中 2已结束 3停用 */
    private Integer status;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
