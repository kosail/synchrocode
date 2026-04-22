package com.frieren.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frieren.service.CollaborativeSessionService;
import jakarta.websocket.CloseReason;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CollaborativeSessionSocketTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldSendInitialStateAndBroadcastStateChanges() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        FakeService service = new FakeService();
        service.activeParticipant = true;
        service.snapshot = new CollaborativeSessionService.SessionSnapshot("", null);

        CollaborativeSessionSocket socketEndpoint = new CollaborativeSessionSocket();
        socketEndpoint.collaborativeSessionService = service;

        TestSocket first = new TestSocket(userId);
        TestSocket second = new TestSocket(userId);

        socketEndpoint.onOpen(first.session(), sessionId.toString());
        socketEndpoint.onOpen(second.session(), sessionId.toString());

        JsonNode initialPayload = MAPPER.readTree(first.sentMessages.getFirst());
        assertEquals("STATE", initialPayload.path("type").asText());
        assertEquals("", initialPayload.path("content").asText());
        assertTrue(initialPayload.path("lockOwner").isNull());

        socketEndpoint.onMessage(first.session(), sessionId.toString(), "{\"type\":\"LOCK\"}");
        JsonNode lockPayload = MAPPER.readTree(second.sentMessages.getLast());
        assertEquals("STATE", lockPayload.path("type").asText());
        assertEquals(userId.toString(), lockPayload.path("lockOwner").asText());

        socketEndpoint.onMessage(first.session(), sessionId.toString(), "{\"type\":\"UPDATE\",\"content\":\"class A {}\"}");
        JsonNode updatePayload = MAPPER.readTree(second.sentMessages.getLast());
        assertEquals("STATE", updatePayload.path("type").asText());
        assertEquals("class A {}", updatePayload.path("content").asText());

        socketEndpoint.onMessage(first.session(), sessionId.toString(), "{\"type\":\"UNLOCK\"}");
        JsonNode unlockPayload = MAPPER.readTree(second.sentMessages.getLast());
        assertEquals("STATE", unlockPayload.path("type").asText());
        assertTrue(unlockPayload.path("lockOwner").isNull());
    }

    @Test
    void shouldCloseConnectionWhenUserIsNotParticipant() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        FakeService service = new FakeService();
        service.activeParticipant = false;

        CollaborativeSessionSocket socketEndpoint = new CollaborativeSessionSocket();
        socketEndpoint.collaborativeSessionService = service;

        TestSocket socket = new TestSocket(userId);
        socketEndpoint.onOpen(socket.session(), sessionId.toString());

        assertNotNull(socket.closeReason);
        assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, socket.closeReason.getCloseCode());
        assertEquals("User is not an active participant", socket.closeReason.getReasonPhrase());
    }

    @Test
    void shouldReturnErrorForUnsupportedMessageType() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        FakeService service = new FakeService();
        service.activeParticipant = true;
        service.snapshot = new CollaborativeSessionService.SessionSnapshot("", null);

        CollaborativeSessionSocket socketEndpoint = new CollaborativeSessionSocket();
        socketEndpoint.collaborativeSessionService = service;

        TestSocket socket = new TestSocket(userId);
        socketEndpoint.onOpen(socket.session(), sessionId.toString());
        socketEndpoint.onMessage(socket.session(), sessionId.toString(), "{\"type\":\"PING\"}");

        JsonNode payload = MAPPER.readTree(socket.sentMessages.getLast());
        assertEquals("ERROR", payload.path("type").asText());
        assertEquals("Unsupported message type", payload.path("message").asText());
    }

    @Test
    void shouldCloseConnectionForInvalidSessionId() throws Exception {
        UUID userId = UUID.randomUUID();

        FakeService service = new FakeService();
        CollaborativeSessionSocket socketEndpoint = new CollaborativeSessionSocket();
        socketEndpoint.collaborativeSessionService = service;

        TestSocket socket = new TestSocket(userId);
        socketEndpoint.onOpen(socket.session(), "invalid-session-id");

        assertNotNull(socket.closeReason);
        assertEquals(CloseReason.CloseCodes.VIOLATED_POLICY, socket.closeReason.getCloseCode());
        assertEquals("Invalid session id", socket.closeReason.getReasonPhrase());
    }

    private static class FakeService extends CollaborativeSessionService {
        private boolean activeParticipant;
        private SessionSnapshot snapshot = new SessionSnapshot("", null);

        @Override
        public boolean isActiveParticipant(UUID sessionId, UUID userId) {
            return activeParticipant;
        }

        @Override
        public SessionSnapshot currentSnapshot(UUID sessionId) {
            return snapshot;
        }

        @Override
        public SessionSnapshot lock(UUID sessionId, UUID userId) {
            snapshot = new SessionSnapshot(snapshot.content(), userId);
            return snapshot;
        }

        @Override
        public SessionSnapshot unlock(UUID sessionId, UUID userId) {
            if (snapshot.lockOwner() != null && snapshot.lockOwner().equals(userId)) {
                snapshot = new SessionSnapshot(snapshot.content(), null);
            }
            return snapshot;
        }

        @Override
        public SessionSnapshot updateContent(UUID sessionId, UUID userId, String content) {
            snapshot = new SessionSnapshot(content, snapshot.lockOwner());
            return snapshot;
        }

        @Override
        public void onSocketDisconnected(UUID sessionId, UUID userId) {
            // no-op for tests
        }
    }

    private static class TestSocket {
        private final Map<String, Object> userProperties = new HashMap<>();
        private final Map<String, List<String>> requestParameterMap;
        private final List<String> sentMessages = new ArrayList<>();
        private final Session session;
        private CloseReason closeReason;

        private TestSocket(UUID userId) {
            requestParameterMap = Map.of("userId", List.of(userId.toString()));

            RemoteEndpoint.Async async = (RemoteEndpoint.Async) Proxy.newProxyInstance(
                    RemoteEndpoint.Async.class.getClassLoader(),
                    new Class[]{RemoteEndpoint.Async.class},
                    (proxy, method, args) -> {
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())) {
                            return proxy == (args != null && args.length > 0 ? args[0] : null);
                        }
                        if ("toString".equals(method.getName())) {
                            return "TestAsyncRemote";
                        }
                        if ("sendText".equals(method.getName()) && args != null && args.length > 0 && args[0] instanceof String text) {
                            sentMessages.add(text);
                        }
                        return null;
                    }
            );

            session = (Session) Proxy.newProxyInstance(
                    Session.class.getClassLoader(),
                    new Class[]{Session.class},
                    (proxy, method, args) -> {
                        if ("hashCode".equals(method.getName())) {
                            return System.identityHashCode(proxy);
                        }
                        if ("equals".equals(method.getName())) {
                            return proxy == (args != null && args.length > 0 ? args[0] : null);
                        }
                        if ("toString".equals(method.getName())) {
                            return "TestSession";
                        }
                        switch (method.getName()) {
                            case "getUserProperties":
                                return userProperties;
                            case "getRequestParameterMap":
                                return requestParameterMap;
                            case "getAsyncRemote":
                                return async;
                            case "close":
                                if (args != null && args.length == 1 && args[0] instanceof CloseReason reason) {
                                    closeReason = reason;
                                }
                                return null;
                            default:
                                if (method.getReturnType().equals(boolean.class)) {
                                    return false;
                                }
                                return null;
                        }
                    }
            );
        }

        private Session session() {
            return session;
        }
    }
}
