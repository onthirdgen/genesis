package com.callaudit.notification.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket message structure matching the frontend protocol.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    private WebSocketMessageType type;
    private Object payload;
    private String timestamp;
    private String callId;
}
