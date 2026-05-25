package com.frieren.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.frieren.service.ChatService;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/ws/chat/{channelId}")
public class ChatSocket {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Map<UUID, Set<Session>> SESSIONS_BY_CHANNEL = new ConcurrentHashMap<>();
    private static final String USER_ID_PROPERTY = "chat.userId";

    @Inject ChatService chatService;
    @Inject ManagedExecutor managedExecutor;

    @OnOpen
    public void onOpen(Session socket, @PathParam("channelId") String channelIdValue) throws IOException {
        UUID channelId = parseChannelId(socket, channelIdValue);
        if (channelId == null) {
            return;
        }

        UUID userId = extractUserId(socket);
        if (userId == null) {
            socket.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid user id"));
            return;
        }

        socket.getUserProperties().put(USER_ID_PROPERTY, userId);
        SESSIONS_BY_CHANNEL.computeIfAbsent(channelId, ignored -> ConcurrentHashMap.newKeySet()).add(socket);

        int page = extractIntQueryParam(socket, "page", 0);
        int size = extractIntQueryParam(socket, "size", 20);
        
        managedExecutor.execute(() -> {
            try {
                sendHistory(socket, chatService.joinChannel(channelId, page, size));
            } catch (IOException e) {
                System.err.println("[ChatWS] Error sending history: " + e.getMessage());
            }
        });
    }

    @OnMessage
    public void onMessage(Session socket, @PathParam("channelId") String channelIdValue, String message) throws IOException {
        System.out.println("[ChatWS] Received message: " + message);
        managedExecutor.execute(() -> {
            try {
                processMessage(socket, channelIdValue, message);
            } catch (Exception e) {
                System.err.println("[ChatWS] Unexpected error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void processMessage(Session socket, String channelIdValue, String message) throws IOException {
        UUID channelId = parseChannelIdOrNull(channelIdValue);
        if (channelId == null) return;

        UUID userId = (UUID) socket.getUserProperties().get(USER_ID_PROPERTY);
        if (userId == null) {
            socket.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid user id"));
            return;
        }

        JsonNode root = MAPPER.readTree(message);
        String type = root.path("type").asText("");
        System.out.println("[ChatWS] Message type: " + type + " from user: " + userId);

        switch (type) {
            case "JOIN" -> sendHistory(socket, chatService.joinChannel(channelId, root.path("page").asInt(0), root.path("size").asInt(20)));
            case "SEND" -> {
                try {
                    ChatService.ChatMessagePayload payload = chatService.sendMessage(
                            channelId,
                            userId,
                            root.path("text").asText(""),
                            readStringArray(root.path("imageUrls"))
                    );
                    System.out.println("[ChatWS] Message persisted: " + payload.id());
                    broadcast(channelId, Map.of("type", "MESSAGE_SENT", "message", payload));
                } catch (Exception e) {
                    System.err.println("[ChatWS] Error sending message: " + e.getMessage());
                    e.printStackTrace();
                    sendError(socket, "Error al guardar el mensaje: " + e.getMessage());
                }
            }
            case "EDIT" -> {
                try {
                    UUID messageId = parseUuid(root.path("messageId").asText(null));
                    if (messageId == null) {
                        sendError(socket, "Message id is required");
                        return;
                    }

                    ChatService.ChatMessagePayload payload = chatService.editMessage(
                            channelId,
                            messageId,
                            userId,
                            root.path("text").asText(""),
                            readStringArray(root.path("imageUrls"))
                    );
                    broadcast(channelId, Map.of("type", "MESSAGE_EDITED", "message", payload));
                } catch (Exception e) {
                    System.err.println("[ChatWS] Error editing message: " + e.getMessage());
                    sendError(socket, "Error al editar el mensaje: " + e.getMessage());
                }
            }
            default -> sendError(socket, "Unsupported message type");
        }
    }

    @OnClose
    public void onClose(Session socket, @PathParam("channelId") String channelIdValue) {
        UUID channelId = parseChannelIdOrNull(channelIdValue);
        if (channelId != null) {
            System.out.println("[ChatWS] Socket closed for channel: " + channelId);
            removeSocket(channelId, socket);
        }
    }

    @OnError
    public void onError(Session socket, @PathParam("channelId") String channelIdValue, Throwable error) {
        UUID channelId = parseChannelIdOrNull(channelIdValue);
        if (channelId != null) {
            System.err.println("[ChatWS] Socket error for channel " + channelId + ": " + error.getMessage());
            removeSocket(channelId, socket);
        }
    }

    private void sendHistory(Session socket, ChatService.ChannelPage page) throws IOException {
        String serialized = MAPPER.writeValueAsString(Map.of(
                "type", "HISTORY",
                "channelId", page.channelId(),
                "page", page.page(),
                "size", page.size(),
                "total", page.total(),
                "messages", page.messages()
        ));
        socket.getAsyncRemote().sendText(serialized);
    }

    private void sendError(Session socket, String message) throws IOException {
        socket.getAsyncRemote().sendText(MAPPER.writeValueAsString(Map.of(
                "type", "ERROR",
                "message", message
        )));
    }

    private void broadcast(UUID channelId, Map<String, Object> payload) throws IOException {
        Set<Session> sockets = SESSIONS_BY_CHANNEL.get(channelId);
        if (sockets == null || sockets.isEmpty()) {
            System.out.println("[ChatWS] No sessions to broadcast for channel: " + channelId);
            return;
        }

        String serialized = MAPPER.writeValueAsString(payload);
        System.out.println("[ChatWS] Broadcasting to " + sockets.size() + " sockets");
        for (Session target : sockets) {
            if (target.isOpen()) {
                target.getAsyncRemote().sendText(serialized);
            }
        }
    }

    private void removeSocket(UUID channelId, Session socket) {
        Set<Session> sockets = SESSIONS_BY_CHANNEL.get(channelId);
        if (sockets == null) {
            return;
        }
        sockets.remove(socket);
        if (sockets.isEmpty()) {
            SESSIONS_BY_CHANNEL.remove(channelId);
        }
    }

    private UUID extractUserId(Session socket) {
        String userIdValue = socket.getRequestParameterMap().getOrDefault("userId", List.of())
                .stream()
                .findFirst()
                .orElse(null);
        return parseUuid(userIdValue);
    }

    private int extractIntQueryParam(Session socket, String key, int defaultValue) {
        String value = socket.getRequestParameterMap().getOrDefault(key, List.of())
                .stream()
                .findFirst()
                .orElse(null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private UUID parseChannelId(Session socket, String channelIdValue) throws IOException {
        UUID channelId = parseChannelIdOrNull(channelIdValue);
        if (channelId == null) {
            socket.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid channel id"));
        }
        return channelId;
    }

    private UUID parseChannelIdOrNull(String channelIdValue) {
        return parseUuid(channelIdValue);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private List<String> readStringArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText());
            }
        }
        return result;
    }
}
