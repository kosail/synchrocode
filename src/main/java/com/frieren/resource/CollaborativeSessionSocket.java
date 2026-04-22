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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/ws/collaborative/{sessionId}")
public class CollaborativeSessionSocket {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<UUID, Set<Session>> SESSIONS_BY_ROOM = new ConcurrentHashMap<>();
    private static final String USER_ID_PROPERTY = "collab.userId";

    @Inject CollaborativeSessionService collaborativeSessionService;

    @OnOpen
    public void onOpen(Session socket, @PathParam("sessionId") String sessionIdValue) throws IOException {
        UUID sessionId = parseSessionId(socket, sessionIdValue);
        if (sessionId == null) {
            return;
        }

        UUID userId = extractUserId(socket);
        if (userId == null || !collaborativeSessionService.isActiveParticipant(sessionId, userId)) {
            socket.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "User is not an active participant"));
            return;
        }

        socket.getUserProperties().put(USER_ID_PROPERTY, userId);
        SESSIONS_BY_ROOM.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(socket);
        sendState(socket, collaborativeSessionService.currentSnapshot(sessionId), "STATE");
    }

    @OnMessage
    public void onMessage(Session socket, @PathParam("sessionId") String sessionIdValue, String message) throws IOException {
        UUID sessionId = parseSessionId(socket, sessionIdValue);
        if (sessionId == null) {
            return;
        }

        UUID userId = (UUID) socket.getUserProperties().get(USER_ID_PROPERTY);
        if (userId == null || !collaborativeSessionService.isActiveParticipant(sessionId, userId)) {
            socket.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "User is not an active participant"));
            return;
        }

        JsonNode root = MAPPER.readTree(message);
        String type = root.path("type").asText("");
        CollaborativeSessionService.SessionSnapshot snapshot;

        switch (type) {
            case "LOCK" -> snapshot = collaborativeSessionService.lock(sessionId, userId);
            case "UNLOCK" -> snapshot = collaborativeSessionService.unlock(sessionId, userId);
            case "UPDATE" -> snapshot = collaborativeSessionService.updateContent(sessionId, userId, root.path("content").asText(""));
            default -> {
                socket.getAsyncRemote().sendText(MAPPER.writeValueAsString(Map.of(
                        "type", "ERROR",
                        "message", "Unsupported message type"
                )));
                return;
            }
        }

        broadcastState(sessionId, snapshot);
    }

    @OnClose
    public void onClose(Session socket, @PathParam("sessionId") String sessionIdValue) {
        UUID sessionId = parseSessionIdOrNull(sessionIdValue);
        if (sessionId == null) {
            return;
        }

        removeSocket(sessionId, socket);
        UUID userId = (UUID) socket.getUserProperties().get(USER_ID_PROPERTY);
        if (userId != null) {
            collaborativeSessionService.onSocketDisconnected(sessionId, userId);
        }
    }

    @OnError
    public void onError(Session socket, @PathParam("sessionId") String sessionIdValue, Throwable error) {
        UUID sessionId = parseSessionIdOrNull(sessionIdValue);
        if (sessionId == null) {
            return;
        }

        removeSocket(sessionId, socket);
    }

    private void sendState(Session socket, CollaborativeSessionService.SessionSnapshot snapshot, String type) throws IOException {
        socket.getAsyncRemote().sendText(MAPPER.writeValueAsString(statePayload(type, snapshot)));
    }

    private void broadcastState(UUID sessionId, CollaborativeSessionService.SessionSnapshot snapshot) throws IOException {
        Set<Session> sockets = SESSIONS_BY_ROOM.get(sessionId);
        if (sockets == null || sockets.isEmpty()) {
            return;
        }

        String payload = MAPPER.writeValueAsString(statePayload("STATE", snapshot));

        for (Session target : sockets) {
            target.getAsyncRemote().sendText(payload);
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
        payload.put("content", snapshot.content());
        payload.put("lockOwner", snapshot.lockOwner() != null ? snapshot.lockOwner().toString() : null);
        return payload;
    }
}
