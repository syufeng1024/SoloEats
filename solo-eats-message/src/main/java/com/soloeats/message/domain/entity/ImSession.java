package com.soloeats.message.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 会话（三端共用）。一行只描述「一个用户 ↔ 一个商家/骑手/客服」，
 * user_id 永远是消费者侧，peer_type/peer_id 永远是被咨询方（2商家 3骑手 4客服）；
 * 商家/骑手侧的「这是我」由 (peer_type, peer_id) 表达，不额外建反向行。
 *
 * <p>未读数用 user_unread / peer_unread 计数列自增与清零，不使用 COUNT(*)。
 * user_hidden / peer_hidden 是两侧独立的「仅隐藏本人」标记（V18 追加），不做物理删除。
 */
@Data
@TableName("t_im_session")
public class ImSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /** 会话编号，IM + 毫秒时间戳 + 4 位随机数 */
    private String sessionNo;

    /** 关联订单 ID；无订单会话允许为空（MySQL 唯一索引不约束 NULL） */
    private Long orderId;

    /** 消费者侧 ID */
    private Long userId;

    /** 2商家 3骑手 4客服 */
    private Integer peerType;

    /** 对方（商家/骑手/客服）ID */
    private Long peerId;

    /** 最近一条消息摘要，纯文本，最长 200 */
    private String lastMessage;

    private LocalDateTime lastMessageAt;

    /** 用户侧未读数 */
    private Integer userUnread;

    /** 对方侧未读数 */
    private Integer peerUnread;

    /** 0进行中 1已结束 2只读 */
    private Integer status;

    /** 0显示 1用户侧隐藏 */
    private Integer userHidden;

    /** 0显示 1对方侧隐藏 */
    private Integer peerHidden;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
