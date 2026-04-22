package com.frieren.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.frieren.service.ChatService;
import jakarta.websocket.CloseReason;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatSocketTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldJoinAndReceiveHistoryAndBroadcastMessages() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        FakeChatService service = new FakeChatService();
        service.joinPage = new ChatService.ChannelPage(channelId, 0, 20, 2, List.of(
                new ChatService.ChatMessagePayload(UUID.randomUUID(), channelId, userId, "new", List.of(), OffsetDateTime.now(), null),
                new ChatService.ChatMessagePayload(UUID.randomUUID(), channelId, userId, "old", List.of(), OffsetDateTime.now().minusMinutes(1), null)
        ));

        ChatSocket socketEndpoint = new ChatSocket();
        socketEndpoint.chatService = service;

        TestSocket first = new TestSocket(userId, Map.of("page", List.of("0"), "size", List.of("20")));
        TestSocket second = new TestSocket(userId, Map.of());

        socketEndpoint.onOpen(first.session(), channelId.toString());
        socketEndpoint.onOpen(second.session(), channelId.toString());

        JsonNode history = MAPPER.readTree(first.sentMessages.getFirst());
        assertEquals("HISTORY", history.path("type").asText());
        assertEquals(2, history.path("messages").size());

        socketEndpoint.onMessage(first.session(), channelId.toString(), "{\"type\":\"SEND\",\"text\":\"hello\",\"imageUrls\":[\"http://img\"]}");
        JsonNode sentPayload = MAPPER.readTree(second.sentMessages.getLast());
        assertEquals("MESSAGE_SENT", sentPayload.path("type").asText());
        assertEquals("hello", sentPayload.path("message").path("text").asText());
        assertEquals(1, sentPayload.path("message").path("imageUrls").size());
    }

    @Test
    void shouldEditMessage() throws Exception {
        UUID channelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        FakeChatService service = new FakeChatService();
        service.joinPage = new ChatService.ChannelPage(channelId, 0, 20, 0, List.of());
        service.editPayload = new ChatService.ChatMessagePayload(
                messageId,
                channelId,
                userId,
                "edited",
                List.of("https://cdn/img.png"),
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now()
        );

        ChatSocket socketEndpoint = new ChatSocket();
        socketEndpoint.chatService = service;

        TestSocket socket = new TestSocket(userId, Map.of());
        socketEndpoint.onOpen(socket.session(), channelId.toString());
        socketEndpoint.onMessage(socket.session(), channelId.toString(),
                "{\"type\":\"EDIT\",\"messageId\":\"" + messageId + "\",\"text\":\"edited\",\"imageUrls\":[\"https://cdn/img.png\"]}");

        JsonNode payload = MAPPER.readTree(socket.sentMessages.getLast());
        assertEquals("MESSAGE_EDITED", payload.path("type").asText());
        assertEquals("edited", payload.path("message").path("text").asText());
    }

    @Test
    void shouldCloseWhenUserIdIsInvalid() throws Exception {
        UUID channelId = UUID.randomUUID();

        FakeChatService service = new FakeChatService();
        service.joinPage = new ChatService.ChannelPage(channelId, 0, 20, 0, List.of());

        ChatSocket socketEndpoint = new ChatSocket();
        socketEndpoint.chatService = service;

        TestSocket socket = new TestSocket(null, Map.of());
        socketEndpoint.onOpen(socket.session(), channelId.toString());

        assertNotNull(socket.closeReason);
        assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, socket.closeReason.getCloseCode());
    }

    static class FakeChatService extends ChatService {
        ChannelPage joinPage;
        ChatMessagePayload editPayload;

        @Override
        public ChannelPage joinChannel(UUID channelId, int page, int size) {
            return joinPage;
        }

        @Override
        public ChatMessagePayload sendMessage(UUID channelId, UUID userId, String body, List<String> imageUrls) {
            return new ChatMessagePayload(
                    UUID.randomUUID(),
                    channelId,
                    userId,
                    body,
                    imageUrls,
                    OffsetDateTime.now(),
                    null
            );
        }

        @Override
        public ChatMessagePayload editMessage(UUID channelId, UUID messageId, UUID userId, String body, List<String> imageUrls) {
            return editPayload;
        }
    }

    static class TestSocket {
        final UUID userId;
        final Map<String, List<String>> queryParams;
        final List<String> sentMessages = new ArrayList<>();
        final Map<String, Object> userProperties = new HashMap<>();
        CloseReason closeReason;

        TestSocket(UUID userId, Map<String, List<String>> queryParams) {
            this.userId = userId;
            this.queryParams = queryParams;
        }

        Session session() {
            RemoteEndpoint.Async async = (RemoteEndpoint.Async) Proxy.newProxyInstance(
                    RemoteEndpoint.Async.class.getClassLoader(),
                    new Class[]{RemoteEndpoint.Async.class},
                    (proxy, method, args) -> {
                        if ("sendText".equals(method.getName()) && args != null && args.length > 0) {
                            sentMessages.add((String) args[0]);
                        }
                        if (method.getReturnType().equals(void.class)) {
                            return null;
                        }
                        return method.getReturnType().isPrimitive() ? 0 : null;
                    }
            );

            return (Session) Proxy.newProxyInstance(
                    Session.class.getClassLoader(),
                    new Class[]{Session.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "equals" -> proxy == (args != null && args.length > 0 ? args[0] : null);
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "getRequestParameterMap" -> {
                            Map<String, List<String>> map = new HashMap<>(queryParams);
                            if (userId != null) {
                                map.put("userId", List.of(userId.toString()));
                            }
                            yield map;
                        }
                        case "getUserProperties" -> userProperties;
                        case "getAsyncRemote" -> async;
                        case "close" -> {
                            if (args != null && args.length > 0 && args[0] instanceof CloseReason reason) {
                                closeReason = reason;
                            }
                            yield null;
                        }
                        default -> method.getReturnType().isPrimitive() ? 0 : null;
                    }
            );
        }
    }
}