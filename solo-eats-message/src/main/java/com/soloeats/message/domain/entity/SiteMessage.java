package com.soloeats.message.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内信。WS 只是加速通道，每条通知必须落此表；
 * read_flag 与 read_at 只通过条件更新修改，未读数依赖 idx_receiver 聚合。
 */
@Data
@TableName("t_site_message")
public class SiteMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 1用户 2商家 3骑手 4平台 */
    private Integer receiverType;

    private Long receiverId;

    private String templateCode;

    /** 1订单 2营销 3系统 */
    private Integer msgType;

    private String title;

    private String content;

    /** 业务类型，如 ORDER */
    private String bizType;

    /** 业务单ID，如订单ID */
    private Long bizId;

    /** 0未读 1已读 */
    private Integer readFlag;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
