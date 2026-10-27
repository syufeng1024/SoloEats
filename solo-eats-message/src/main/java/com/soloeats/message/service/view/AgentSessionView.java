package com.soloeats.message.service.view;

import com.soloeats.message.domain.entity.ImSession;

import java.time.LocalDateTime;

/**
 * 坐席端客服会话视图（peer_type = 4）。
 *
 * <p>与 C 端 {@link ImSessionView} 的区别：带 agentId（接待坐席 t_sys_user.id）与 status，
 * 不再区分「当前主体是哪一侧」。userUnread 是消费者侧未读数，坐席侧未读见 peer_unread，
 * 本阶段坐席端列表只需要展示会话本身，故不返回 peerUnread。
 */
public record AgentSessionView(Long id, String sessionNo, Long userId, Long orderId,
                               Integer peerType, Long peerId, Long agentId,
                               String lastMessage, LocalDateTime lastMessageAt,
                               Integer userUnread, Integer status, LocalDateTime createdAt) {

    public static AgentSessionView from(ImSession session) {
        return new AgentSessionView(
                session.getId(),
                session.getSessionNo(),
                session.getUserId(),
                session.getOrderId(),
                session.getPeerType(),
                session.getPeerId(),
                session.getAgentId(),
                session.getLastMessage(),
                session.getLastMessageAt(),
                session.getUserUnread(),
                session.getStatus(),
                session.getCreatedAt()
        );
    }
}
