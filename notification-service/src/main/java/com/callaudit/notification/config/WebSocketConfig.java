package com.callaudit.notification.config;

import com.callaudit.notification.websocket.CallUpdatesWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket Configuration for real-time call updates.
 *
 * Registers the WebSocket handler at /ws/calls/{callId} endpoint.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CallUpdatesWebSocketHandler callUpdatesWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Use wildcard pattern - callId is extracted from URI in the handler
        registry.addHandler(callUpdatesWebSocketHandler, "/ws/calls/*")
                .setAllowedOrigins("*"); // CORS handled by API Gateway
    }
}
