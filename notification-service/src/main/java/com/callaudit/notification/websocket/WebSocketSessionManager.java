package com.callaudit.notification.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages WebSocket sessions for call updates.
 *
 * Maintains a mapping of callId to connected WebSocket sessions,
 * allowing messages to be broadcast to all clients watching a specific call.
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    // Map of callId -> Set of WebSocket sessions
    private final Map<String, Set<WebSocketSession>> callSessions = new ConcurrentHashMap<>();

    // Map of sessionId -> callId for reverse lookup
    private final Map<String, String> sessionToCall = new ConcurrentHashMap<>();

    /**
     * Register a session for a specific call.
     *
     * @param callId  The call ID to subscribe to
     * @param session The WebSocket session
     */
    public void registerSession(String callId, WebSocketSession session) {
        callSessions.computeIfAbsent(callId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionToCall.put(session.getId(), callId);
        log.info("Session {} registered for call {}", session.getId(), callId);
    }

    /**
     * Remove a session from all subscriptions.
     *
     * @param session The WebSocket session to remove
     */
    public void removeSession(WebSocketSession session) {
        String callId = sessionToCall.remove(session.getId());
        if (callId != null) {
            Set<WebSocketSession> sessions = callSessions.get(callId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    callSessions.remove(callId);
                }
            }
            log.info("Session {} removed from call {}", session.getId(), callId);
        }
    }

    /**
     * Send a message to all sessions subscribed to a specific call.
     *
     * @param callId  The call ID
     * @param message The message to send (JSON string)
     */
    public void sendToCall(String callId, String message) {
        Set<WebSocketSession> sessions = callSessions.get(callId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("No sessions registered for call {}", callId);
            return;
        }

        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
                    removeSession(session);
                }
            } else {
                removeSession(session);
            }
        }

        log.debug("Sent message to {} sessions for call {}", sessions.size(), callId);
    }

    /**
     * Get the number of active sessions for a call.
     *
     * @param callId The call ID
     * @return Number of active sessions
     */
    public int getSessionCount(String callId) {
        Set<WebSocketSession> sessions = callSessions.get(callId);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * Get total number of active sessions across all calls.
     *
     * @return Total session count
     */
    public int getTotalSessionCount() {
        return sessionToCall.size();
    }

    /**
     * Check if there are any sessions for a specific call.
     *
     * @param callId The call ID
     * @return true if there are active sessions
     */
    public boolean hasSessionsForCall(String callId) {
        Set<WebSocketSession> sessions = callSessions.get(callId);
        return sessions != null && !sessions.isEmpty();
    }
}
