package com.soloeats.message.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单实例 WS 会话注册表，key = {receiverType}:{receiverId}。
 *
 * <p>空的 key 不删除：删除需要与并发注册做「集合是否已空」的二次判定，
 * 一旦判断失误会把新连接挂到已被移除的集合上，消息永久丢失。
 * 空集合的内存开销按接收者数量线性增长，可接受。
 *
 * <p>TODO(P1 多实例)：多节点部署时本 Map 只覆盖本机连接，
 * 需由 PushService 通过 Redis Pub/Sub 广播到各节点再调用 {@link #sendTo}。
 */
@Slf4j
@Component
public class WebSocketSessionRegistry {

    /** 会话属性：注册 key，注销时无需调用方再传 receiverType/receiverId。 */
    public static final String ATTR_REGISTRY_KEY = "ws.registryKey";

    /** 会话属性：最近一次心跳时间戳（毫秒）。 */
    public static final String ATTR_LAST_PING = "ws.lastPingAt";

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public static String key(Integer receiverType, Long receiverId) {
        return receiverType + ":" + receiverId;
    }

    public void register(Integer receiverType, Long receiverId, WebSocketSession session) {
        String key = key(receiverType, receiverId);
        session.getAttributes().put(ATTR_REGISTRY_KEY, key);
        session.getAttributes().put(ATTR_LAST_PING, System.currentTimeMillis());
        sessions.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("ws connected key={} onlineSessions={}", key, onlineCount());
    }

    public void unregister(WebSocketSession session) {
        Object key = session.getAttributes().get(ATTR_REGISTRY_KEY);
        if (key == null) {
            return;
        }
        Set<WebSocketSession> set = sessions.get(key.toString());
        if (set != null) {
            set.remove(session);
        }
        log.info("ws disconnected key={} onlineSessions={}", key, onlineCount());
    }

    /** 心跳时间写入会话属性；连接存活判断仍以 session.isOpen() 为准。 */
    public void touch(WebSocketSession session) {
        session.getAttributes().put(ATTR_LAST_PING, System.currentTimeMillis());
    }

    /**
     * 向该接收者当前节点上的全部会话下发。返回是否至少送达一个会话；
     * 全部离线或全部发送失败都返回 false，由调用方决定落 offline 日志。
     */
    public boolean sendTo(Integer receiverType, Long receiverId, String payload) {
        Set<WebSocketSession> set = sessions.get(key(receiverType, receiverId));
        if (set == null || set.isEmpty()) {
            return false;
        }
        boolean delivered = false;
        for (WebSocketSession session : set) {
            if (!session.isOpen()) {
                unregister(session);
                continue;
            }
            // WebSocketSession 不是线程安全的，并发 sendMessage 会抛 IllegalStateException
            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(payload));
                        delivered = true;
                    }
                }
            } catch (Exception ex) {
                log.warn("ws send failed key={} reason={}",
                        key(receiverType, receiverId), ex.getMessage());
                closeQuietly(session);
                unregister(session);
            }
        }
        return delivered;
    }

    public int onlineCount() {
        return sessions.values().stream().mapToInt(Set::size).sum();
    }

    public int sessionCount(Integer receiverType, Long receiverId) {
        Set<WebSocketSession> set = sessions.get(key(receiverType, receiverId));
        return set == null ? 0 : set.size();
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ex) {
            log.debug("ws close ignored reason={}", ex.getMessage());
        }
    }
}
