package com.soloeats.app.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.auth.JwtUtils;
import com.soloeats.common.auth.UserPrincipal;
import com.soloeats.message.domain.enums.ReceiverType;
import com.soloeats.message.ws.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * /ws 连接入口。
 *
 * <p>鉴权只认 query 里的 token：握手是 HTTP 请求但不带 Authorization 头，
 * 且绝不能信任前端传来的 userId。校验失败一律 1008 关闭，
 * 不让未授权连接进入注册表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageWebSocketHandler extends TextWebSocketHandler {

    /** 1008 = Policy Violation。 */
    private static final int CLOSE_POLICY_VIOLATION = 1008;
    private static final String ATTR_TENANT_ID = "tenantId";

    private final JwtUtils jwtUtils;
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = queryParam(session.getUri(), "token");
        if (!StringUtils.hasText(token)) {
            reject(session, "missing token");
            return;
        }

        UserPrincipal principal;
        try {
            principal = jwtUtils.parse(token.strip());
        } catch (Exception ex) {
            reject(session, "invalid token");
            return;
        }

        Integer receiverType = ReceiverType.resolve(principal.roles());
        if (principal.userId() == null || receiverType == null) {
            reject(session, "unresolved identity");
            return;
        }

        session.getAttributes().put(ATTR_TENANT_ID, principal.tenantId());
        sessionRegistry.register(receiverType, principal.userId(), session);
        send(session, Map.of("type", "CONNECTED"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        sessionRegistry.touch(session);
        String payload = message.getPayload() == null ? "" : message.getPayload().strip();
        // 兼容纯文本 ping 与 {"type":"ping"} 两种客户端写法
        if ("ping".equalsIgnoreCase(payload) || payload.contains("\"ping\"")) {
            send(session, Map.of("type", "PONG"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("ws transport error sessionId={} reason={}", session.getId(), exception.getMessage());
        sessionRegistry.unregister(session);
    }

    private String queryParam(URI uri, String name) {
        if (uri == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
    }

    private void reject(WebSocketSession session, String reason) {
        log.warn("ws handshake rejected sessionId={} reason={}", session.getId(), reason);
        try {
            session.close(new CloseStatus(CLOSE_POLICY_VIOLATION, reason));
        } catch (Exception ex) {
            log.debug("ws close after reject ignored reason={}", ex.getMessage());
        }
    }

    private void send(WebSocketSession session, Map<String, Object> body) {
        try {
            String payload = objectMapper.writeValueAsString(body);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        } catch (Exception ex) {
            log.warn("ws send failed sessionId={} reason={}", session.getId(), ex.getMessage());
        }
    }
}
