# WebSocket Implementation Guide

This document explains the WebSocket implementation added to the notification-service for real-time call updates.

## Overview

The WebSocket server allows frontend clients to receive real-time updates about call processing (transcription started, progress, completed, etc.) without polling the API.

**Flow:**
```
[Kafka Events] → [NotificationEventListener] → [WebSocketNotificationService] → [WebSocketSessionManager] → [Connected Clients]
```

---

## Files Changed/Created

### 1. pom.xml (Modified)

**Location:** `notification-service/pom.xml`

**Change:** Added WebSocket dependency:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

This brings in Spring's WebSocket support, including the `TextWebSocketHandler` base class and `WebSocketConfigurer` interface.

---

### 2. WebSocketConfig.java (New)

**Location:** `notification-service/src/main/java/com/callaudit/notification/config/WebSocketConfig.java`

**Purpose:** Registers WebSocket handlers with Spring's WebSocket infrastructure.

```java
@Configuration
@EnableWebSocket  // Enables Spring WebSocket support
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final CallUpdatesWebSocketHandler callUpdatesWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register our handler at /ws/calls/* endpoint
        // The wildcard (*) matches any callId in the path
        registry.addHandler(callUpdatesWebSocketHandler, "/ws/calls/*")
                .setAllowedOrigins("*"); // CORS - allow all origins (gateway handles CORS)
    }
}
```

**Key Concepts:**
- `@EnableWebSocket` - Tells Spring to set up WebSocket infrastructure
- `WebSocketConfigurer` - Interface to customize WebSocket configuration
- `WebSocketHandlerRegistry` - Where you register your handlers and their URL paths
- `.setAllowedOrigins("*")` - Allows connections from any origin (needed for browser clients)

---

### 3. CallUpdatesWebSocketHandler.java (New)

**Location:** `notification-service/src/main/java/com/callaudit/notification/websocket/CallUpdatesWebSocketHandler.java`

**Purpose:** Handles individual WebSocket connections - connection lifecycle and incoming messages.

```java
@Component
public class CallUpdatesWebSocketHandler extends TextWebSocketHandler {

    private static final UriTemplate URI_TEMPLATE = new UriTemplate("/ws/calls/{callId}");

    // Called when a client connects
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String callId = extractCallId(session);  // Get callId from URL path
        sessionManager.registerSession(callId, session);  // Track this session

        // Send confirmation message back to client
        WebSocketMessage connectedMessage = WebSocketMessage.builder()
                .type(WebSocketMessageType.CONNECTED)
                .callId(callId)
                .payload(Map.of("callId", callId, "message", "Connected to call updates"))
                .timestamp(Instant.now().toString())
                .build();
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(connectedMessage)));
    }

    // Called when client disconnects
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.removeSession(session);  // Clean up
    }

    // Called when client sends a message
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Parse and handle incoming messages (CONNECT, DISCONNECT, etc.)
    }

    // Called on network errors
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessionManager.removeSession(session);  // Clean up on error
    }

    // Extract callId from URL like /ws/calls/abc-123
    private String extractCallId(WebSocketSession session) {
        String path = session.getUri().getPath();
        Map<String, String> variables = URI_TEMPLATE.match(path);
        return variables != null ? variables.get("callId") : null;
    }
}
```

**Key Concepts:**
- `TextWebSocketHandler` - Base class for handling text-based WebSocket messages (vs binary)
- `WebSocketSession` - Represents a single client connection; used to send messages
- `afterConnectionEstablished()` - Called once when handshake completes
- `handleTextMessage()` - Called for each message the client sends
- `UriTemplate` - Spring utility to extract path variables from URLs

---

### 4. WebSocketSessionManager.java (New)

**Location:** `notification-service/src/main/java/com/callaudit/notification/websocket/WebSocketSessionManager.java`

**Purpose:** Tracks which WebSocket sessions are watching which calls. Enables sending messages to all clients interested in a specific call.

```java
@Component
public class WebSocketSessionManager {

    // Map: callId -> Set of sessions watching that call
    // ConcurrentHashMap for thread safety (multiple threads may access)
    private final Map<String, Set<WebSocketSession>> callSessions = new ConcurrentHashMap<>();

    // Reverse map: sessionId -> callId (for cleanup when session closes)
    private final Map<String, String> sessionToCall = new ConcurrentHashMap<>();

    // Register a session for a specific call
    public void registerSession(String callId, WebSocketSession session) {
        callSessions.computeIfAbsent(callId, k -> ConcurrentHashMap.newKeySet())
                    .add(session);
        sessionToCall.put(session.getId(), callId);
    }

    // Remove a session (called when client disconnects)
    public void removeSession(WebSocketSession session) {
        String callId = sessionToCall.remove(session.getId());
        if (callId != null) {
            Set<WebSocketSession> sessions = callSessions.get(callId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    callSessions.remove(callId);  // Clean up empty sets
                }
            }
        }
    }

    // Send a message to ALL sessions watching a specific call
    public void sendToCall(String callId, String message) {
        Set<WebSocketSession> sessions = callSessions.get(callId);
        if (sessions != null) {
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    // Check if anyone is watching a call (optimization to skip work)
    public boolean hasSessionsForCall(String callId) {
        Set<WebSocketSession> sessions = callSessions.get(callId);
        return sessions != null && !sessions.isEmpty();
    }
}
```

**Key Concepts:**
- `ConcurrentHashMap` - Thread-safe map for concurrent access
- `ConcurrentHashMap.newKeySet()` - Thread-safe Set implementation
- Two maps maintained: forward (callId→sessions) and reverse (sessionId→callId) for efficient lookup in both directions

---

### 5. WebSocketNotificationService.java (New)

**Location:** `notification-service/src/main/java/com/callaudit/notification/websocket/WebSocketNotificationService.java`

**Purpose:** High-level service for sending typed notifications. Used by Kafka listeners to push updates.

```java
@Service
public class WebSocketNotificationService {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    // Notify clients that transcription started
    public void notifyTranscriptionStarted(String callId, Integer estimatedDuration) {
        sendMessage(callId, WebSocketMessageType.TRANSCRIPTION_STARTED, Map.of(
                "callId", callId,
                "estimatedDuration", estimatedDuration != null ? estimatedDuration : 0
        ));
    }

    // Notify clients of progress (0-100%)
    public void notifyTranscriptionProgress(String callId, int progress,
                                            Integer currentSegment, Integer totalSegments) {
        // ... build payload and send
    }

    // Notify clients transcription is done
    public void notifyTranscriptionCompleted(String callId, String transcriptionId,
                                             int totalSegments, long duration) {
        // ... build payload and send
    }

    // Generic method to send any message type
    private void sendMessage(String callId, WebSocketMessageType type, Object payload) {
        // Skip if no one is listening (optimization)
        if (!sessionManager.hasSessionsForCall(callId)) {
            return;
        }

        // Build message object
        WebSocketMessage message = WebSocketMessage.builder()
                .type(type)
                .callId(callId)
                .payload(payload)
                .timestamp(Instant.now().toString())
                .build();

        // Serialize to JSON and send
        String jsonMessage = objectMapper.writeValueAsString(message);
        sessionManager.sendToCall(callId, jsonMessage);
    }
}
```

**Key Concepts:**
- Provides typed methods (notifyTranscriptionStarted, etc.) for cleaner calling code
- Checks `hasSessionsForCall()` before doing work (avoids serialization if no one is listening)
- Uses Jackson `ObjectMapper` to convert messages to JSON

---

### 6. WebSocketMessage.java (New)

**Location:** `notification-service/src/main/java/com/callaudit/notification/websocket/WebSocketMessage.java`

**Purpose:** DTO representing the JSON structure sent over WebSocket.

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    private WebSocketMessageType type;  // e.g., TRANSCRIPTION_STARTED
    private Object payload;              // The actual data (varies by type)
    private String timestamp;            // ISO timestamp
    private String callId;               // Which call this relates to
}
```

**Example JSON sent to client:**
```json
{
  "type": "TRANSCRIPTION_COMPLETED",
  "callId": "abc-123",
  "payload": {
    "callId": "abc-123",
    "transcriptionId": "abc-123",
    "totalSegments": 15,
    "duration": 180000
  },
  "timestamp": "2026-01-03T00:00:00.000Z"
}
```

---

### 7. WebSocketMessageType.java (New)

**Location:** `notification-service/src/main/java/com/callaudit/notification/websocket/WebSocketMessageType.java`

**Purpose:** Enum of all message types. Matches what the frontend expects.

```java
public enum WebSocketMessageType {
    // Connection lifecycle
    CONNECT,              // Client → Server: I want to connect
    CONNECTED,            // Server → Client: Connection confirmed
    DISCONNECT,           // Client → Server: I'm disconnecting
    ERROR,                // Server → Client: Something went wrong

    // Transcription progress
    TRANSCRIPTION_STARTED,    // Processing has begun
    TRANSCRIPTION_PROGRESS,   // X% complete
    TRANSCRIPTION_SEGMENT,    // A new segment is available
    TRANSCRIPTION_COMPLETED,  // All done
    TRANSCRIPTION_FAILED,     // Something went wrong

    // General status
    CALL_STATUS_UPDATE        // Status changed (RECEIVED, TRANSCRIBED, etc.)
}
```

---

### 8. NotificationEventListener.java (Modified)

**Location:** `notification-service/src/main/java/com/callaudit/notification/listener/NotificationEventListener.java`

**Changes:** Added two new Kafka listeners to trigger WebSocket notifications.

```java
@Component
public class NotificationEventListener {

    private final WebSocketNotificationService webSocketNotificationService;  // NEW

    // NEW: Listen for call received events
    @KafkaListener(topics = "calls.received", groupId = "notification-service-websocket")
    public void handleCallReceived(String message) {
        JsonNode event = objectMapper.readTree(message);
        String callId = extractCallId(event);

        // Push to WebSocket clients
        webSocketNotificationService.notifyTranscriptionStarted(callId, null);
        webSocketNotificationService.notifyCallStatusUpdate(callId, "RECEIVED",
                                                            "Call received, processing started");
    }

    // NEW: Listen for transcription completed events
    @KafkaListener(topics = "calls.transcribed", groupId = "notification-service-websocket")
    public void handleCallTranscribed(String message) {
        JsonNode event = objectMapper.readTree(message);
        String callId = extractCallId(event);
        JsonNode payload = event.get("payload");

        // Extract details from the event
        int segmentCount = 0;
        long duration = 0;
        if (payload != null) {
            if (payload.has("segments")) {
                segmentCount = payload.get("segments").size();
            }
            if (payload.has("totalDuration")) {
                duration = payload.get("totalDuration").asLong();
            }
        }

        // Push to WebSocket clients
        webSocketNotificationService.notifyTranscriptionProgress(callId, 100, segmentCount, segmentCount);
        webSocketNotificationService.notifyTranscriptionCompleted(callId, callId, segmentCount, duration);
        webSocketNotificationService.notifyCallStatusUpdate(callId, "TRANSCRIBED", "Transcription completed");
    }
}
```

**Key Concepts:**
- `@KafkaListener` - Spring Kafka annotation that subscribes to a topic
- `groupId` - Kafka consumer group; using a different group ("notification-service-websocket") means these listeners get their own copy of events
- When events arrive, we extract data and call `WebSocketNotificationService` methods

---

### 9. API Gateway application.yml (Modified)

**Location:** `api-gateway/src/main/resources/application.yml`

**Change:** Added WebSocket route:

```yaml
routes:
  # ... existing routes ...

  # WebSocket - Call Updates
  - id: websocket-call-updates
    uri: ws://notification-service:8080   # Note: ws:// not http://
    predicates:
      - Path=/ws/calls/**
```

**Key Concepts:**
- `ws://` protocol tells Spring Cloud Gateway this is a WebSocket route
- Gateway automatically handles the HTTP→WebSocket upgrade
- No filters applied (WebSocket connections are long-lived, filters don't make sense)

---

## How It All Works Together

### Client Connection Flow:

1. **Frontend connects:** `new WebSocket('ws://localhost:8080/ws/calls/abc-123')`

2. **Gateway routes:** Matches `/ws/calls/**`, forwards to `ws://notification-service:8080/ws/calls/abc-123`

3. **Handler accepts:** `CallUpdatesWebSocketHandler.afterConnectionEstablished()` is called
   - Extracts `callId` = "abc-123" from URL
   - Registers session with `WebSocketSessionManager`
   - Sends `CONNECTED` message back to client

4. **Client receives:** `{"type":"CONNECTED","callId":"abc-123",...}`

### Event Notification Flow:

1. **Call uploaded:** call-ingestion-service publishes to `calls.received` Kafka topic

2. **Listener triggers:** `NotificationEventListener.handleCallReceived()` receives the event

3. **Service notifies:** `WebSocketNotificationService.notifyTranscriptionStarted(callId, null)`
   - Checks if anyone is watching this callId
   - If yes, builds `WebSocketMessage` with type `TRANSCRIPTION_STARTED`
   - Serializes to JSON
   - Calls `sessionManager.sendToCall(callId, json)`

4. **Manager broadcasts:** `WebSocketSessionManager.sendToCall()`
   - Looks up all sessions for this callId
   - Sends the message to each open session

5. **Client receives:** `{"type":"TRANSCRIPTION_STARTED","callId":"abc-123",...}`

---

## Testing

Since WebSocket requires a proper upgrade handshake, testing with curl shows:

```bash
# Direct to notification-service
curl http://localhost:8087/ws/calls/test-123
# Returns: Can "Upgrade" only to "WebSocket".

# This is CORRECT - it proves the endpoint exists and rejects non-WebSocket requests
```

To properly test, use the frontend or a WebSocket client tool like `wscat`:
```bash
npm install -g wscat
wscat -c ws://localhost:8080/ws/calls/test-123
```

---

## Summary

| Component | Responsibility |
|-----------|----------------|
| `WebSocketConfig` | Registers handlers with Spring |
| `CallUpdatesWebSocketHandler` | Handles connection lifecycle |
| `WebSocketSessionManager` | Tracks sessions per callId |
| `WebSocketNotificationService` | High-level notification methods |
| `WebSocketMessage` | JSON structure |
| `WebSocketMessageType` | Enum of message types |
| `NotificationEventListener` | Bridges Kafka → WebSocket |
