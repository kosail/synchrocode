package com.frieren.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frieren.service.CollaborativeSessionService;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/ws/collaborative/{sessionId}")
public class CollaborativeSessionSocket {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<UUID, Set<Session>> SESSIONS_BY_ROOM = new ConcurrentHashMap<>();
    private static final Map<String, UUID> SOCKET_TO_USER = new ConcurrentHashMap<>();
    private static final String USER_ID_PROPERTY = "collab.userId";

    @Inject CollaborativeSessionService collaborativeSessionService;
    @Inject org.eclipse.microprofile.context.ManagedExecutor managedExecutor;

    @OnOpen
    public void onOpen(Session socket, @PathParam("sessionId") String sessionIdValue) {
        managedExecutor.execute(() -> {
            try {
                handleOpen(socket, sessionIdValue);
            } catch (Exception e) {
                System.err.println("Fatal error in WS open handler: " + e.getMessage());
            }
        });
    }

    private void handleOpen(Session socket, String sessionIdValue) throws IOException {
        UUID sessionId = parseSessionId(socket, sessionIdValue);
        if (sessionId == null) {
            System.err.println("WS Rejected: sessionId is null");
            return;
        }

        UUID userId = extractUserId(socket);
        if (userId == null) {
            System.err.println("WS Rejected: userId is null");
            socket.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "User ID not provided"));
            return;
        }

        // Asegurar que el usuario esté marcado como activo y la sesión re-activada si es necesario
        collaborativeSessionService.autoJoinIfMissing(sessionId, userId);

        System.out.println("WS Connected: sessionId=" + sessionId + ", userId=" + userId);
        SOCKET_TO_USER.put(socket.getId(), userId);
        socket.getUserProperties().put(USER_ID_PROPERTY, userId);
        SESSIONS_BY_ROOM.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(socket);

        sendState(socket, collaborativeSessionService.currentSnapshot(sessionId), "STATE");
    }

    @OnMessage(maxMessageSize = 10485760)
    public void onMessage(Session socket, @PathParam("sessionId") String sessionIdValue, String message) {
        managedExecutor.execute(() -> {
            try {
                handleMessage(socket, sessionIdValue, message);
            } catch (Exception e) {
                System.err.println("Fatal error in WS message handler: " + e.getMessage());
            }
        });
    }

    private void handleMessage(Session socket, String sessionIdValue, String message) throws IOException {
        UUID sessionId = parseSessionId(socket, sessionIdValue);
        if (sessionId == null) {
            return;
        }

        UUID userId = SOCKET_TO_USER.get(socket.getId());
        if (userId == null) {
            userId = extractUserId(socket);
            if (userId != null) {
                SOCKET_TO_USER.put(socket.getId(), userId);
            } else {
                socket.getBasicRemote().sendText(MAPPER.writeValueAsString(Map.of(
                        "type", "ERROR",
                        "message", "User ID not found in session"
                )));
                socket.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "User ID not found in session"));
                return;
            }
        }

        try {
            JsonNode root = MAPPER.readTree(message);
            String type = root.path("type").asText("");
            CollaborativeSessionService.SessionSnapshot snapshot;

            switch (type) {
                case "LOCK" -> snapshot = collaborativeSessionService.lock(sessionId, userId);
                case "UNLOCK" -> snapshot = collaborativeSessionService.unlock(sessionId, userId);
                case "UPDATE" -> snapshot = collaborativeSessionService.updateContent(sessionId, userId, root.path("fileName").asText(""), root.path("content").asText(""));
                case "TEXT", "SYSTEM", "GIT_LINK" -> {
                    String content = root.path("content").asText("");
                    if (content.isBlank()) {
                        socket.getBasicRemote().sendText(MAPPER.writeValueAsString(Map.of(
                                "type", "ERROR",
                                "message", "Message content is required"
                        )));
                        return;
                    }
                    broadcast(sessionId, chatPayload(type, userId, content));
                    return;
                }
                default -> {
                    socket.getBasicRemote().sendText(MAPPER.writeValueAsString(Map.of(
                            "type", "ERROR",
                            "message", "Unsupported message type"
                    )));
                    return;
                }
            }

            broadcastState(sessionId, snapshot);
        } catch (Exception e) {
            System.err.println("Error processing WS message: " + e.getMessage());
            if (socket.isOpen()) {
                try {
                    socket.getBasicRemote().sendText(MAPPER.writeValueAsString(Map.of(
                            "type", "ERROR",
                            "message", e.getMessage() != null ? e.getMessage() : "Internal server error"
                    )));
                } catch (Exception ex) {
                    System.err.println("Failed to send ERROR payload: " + ex.getMessage());
                }
            }
        }
    }

    @OnClose
    public void onClose(Session socket, @PathParam("sessionId") String sessionIdValue) {
        managedExecutor.execute(() -> {
            try {
                handleClose(socket, sessionIdValue);
            } catch (Exception e) {
                System.err.println("Fatal error in WS close handler: " + e.getMessage());
            }
        });
    }

    private void handleClose(Session socket, String sessionIdValue) {
        UUID sessionId = parseSessionIdOrNull(sessionIdValue);
        if (sessionId == null) {
            return;
        }
        handleSocketTermination(sessionId, socket);
    }

    @OnError
    public void onError(Session socket, @PathParam("sessionId") String sessionIdValue, Throwable error) {
        UUID sessionId = parseSessionIdOrNull(sessionIdValue);
        if (sessionId == null) {
            return;
        }

        handleSocketTermination(sessionId, socket);
    }

    private void handleSocketTermination(UUID sessionId, Session socket) {
        removeSocket(sessionId, socket);
        UUID userId = SOCKET_TO_USER.remove(socket.getId());
        if (userId == null) {
            userId = (UUID) socket.getUserProperties().get(USER_ID_PROPERTY);
        }
        
        if (userId != null) {
            if (!hasSocketForUser(sessionId, userId)) {
                // collaborativeSessionService.onSocketDisconnected(sessionId, userId);
            }
        }
    }

    private void sendState(Session socket, CollaborativeSessionService.SessionSnapshot snapshot, String type) throws IOException {
        synchronized (socket) {
            socket.getBasicRemote().sendText(MAPPER.writeValueAsString(statePayload(type, snapshot)));
        }
    }

    private void broadcastState(UUID sessionId, CollaborativeSessionService.SessionSnapshot snapshot) throws IOException {
        broadcast(sessionId, statePayload("STATE", snapshot));
    }

    private void broadcast(UUID sessionId, Map<String, Object> payloadMap) throws IOException {
        Set<Session> sockets = SESSIONS_BY_ROOM.get(sessionId);
        if (sockets == null || sockets.isEmpty()) {
            return;
        }

        String payload = MAPPER.writeValueAsString(payloadMap);

        for (Session target : sockets) {
            if (target.isOpen()) {
                try {
                    target.getBasicRemote().sendText(payload);
                } catch (Exception e) {
                    System.err.println("Failed to send payload to target: " + e.getMessage());
                }
            }
        }
    }

    private void removeSocket(UUID sessionId, Session socket) {
        Set<Session> sockets = SESSIONS_BY_ROOM.get(sessionId);
        if (sockets == null) {
            return;
        }
        sockets.remove(socket);
        if (sockets.isEmpty()) {
            SESSIONS_BY_ROOM.remove(sessionId);
        }
    }

    private boolean hasSocketForUser(UUID sessionId, UUID userId) {
        Set<Session> sockets = SESSIONS_BY_ROOM.get(sessionId);
        if (sockets == null || sockets.isEmpty()) {
            return false;
        }

        for (Session target : sockets) {
            Object targetUser = SOCKET_TO_USER.get(target.getId());
            if (targetUser == null) {
                 targetUser = target.getUserProperties().get(USER_ID_PROPERTY);
            }
            if (userId.equals(targetUser)) {
                return true;
            }
        }
        return false;
    }

    private UUID extractUserId(Session socket) {
        String userIdValue = socket.getRequestParameterMap().getOrDefault("userId", java.util.List.of())
                .stream()
                .findFirst()
                .orElse(null);
        if (userIdValue == null || userIdValue.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(userIdValue);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private UUID parseSessionId(Session socket, String sessionIdValue) throws IOException {
        UUID sessionId = parseSessionIdOrNull(sessionIdValue);
        if (sessionId == null) {
            socket.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid session id"));
        }
        return sessionId;
    }

    private UUID parseSessionIdOrNull(String sessionIdValue) {
        if (sessionIdValue == null || sessionIdValue.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(sessionIdValue);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Map<String, Object> statePayload(String type, CollaborativeSessionService.SessionSnapshot snapshot) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", type);
        payload.put("files", snapshot.files());
        payload.put("lockOwner", snapshot.lockOwner() != null ? snapshot.lockOwner().toString() : null);
        return payload;
    }

    private Map<String, Object> chatPayload(String type, UUID senderId, String content) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", type);
        payload.put("senderId", senderId.toString());
        payload.put("content", content);
        payload.put("timestamp", OffsetDateTime.now().toString());
        return payload;
    }
}
