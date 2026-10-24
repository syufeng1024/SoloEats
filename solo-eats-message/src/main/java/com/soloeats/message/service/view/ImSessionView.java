package com.soloeats.message.service.view;

import com.soloeats.message.domain.entity.ImSession;

import java.time.LocalDateTime;

/**
 * 会话列表/创建返回值。unread 是「当前调用方自己」的未读数：
 * 用户侧取 user_unread，商家/骑手/客服侧取 peer_unread。
 */
public record ImSessionView(
        Long id,
        String sessionNo,
        Long orderId,
        Long userId,
        Integer peerType,
        Long peerId,
        String lastMessage,
        LocalDateTime lastMessageAt,
        Integer unread,
        Integer status,
        LocalDateTime createdAt
) {

    public static ImSessionView from(ImSession session, boolean userSide) {
        Integer unread = userSide ? session.getUserUnread() : session.getPeerUnread();
        return new ImSessionView(
                session.getId(),
                session.getSessionNo(),
                session.getOrderId(),
                session.getUserId(),
                session.getPeerType(),
                session.getPeerId(),
                session.getLastMessage(),
                session.getLastMessageAt(),
                unread == null ? 0 : unread,
                session.getStatus(),
                session.getCreatedAt());
    }
}
