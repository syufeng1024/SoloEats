package com.soloeats.app.config;

import com.soloeats.app.ws.MessageWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 原生 WebSocket，不引入 STOMP / SockJS：
 * 三端只需要一条接收服务端推送的单向通道，心跳由 ping/pong 文本帧完成。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageWebSocketHandler messageWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 移动端与 H5 的 Origin 不固定，鉴权已由 token 保证，这里放开跨域
        registry.addHandler(messageWebSocketHandler, "/ws")
                .setAllowedOriginPatterns("*");
    }
}
