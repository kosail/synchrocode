package com.frieren.service;

import com.frieren.dto.CollaborativeSessionSummaryResponse;
import com.frieren.dto.CreateCollaborativeSessionRequest;
import com.frieren.dto.JoinCollaborativeSessionResponse;
import com.frieren.dto.StartCollaborativeSessionResponse;
import com.frieren.entity.CollaborativeSession;
import com.frieren.entity.Project;
import com.frieren.entity.SessionParticipant;
import com.frieren.entity.SessionParticipantId;
import com.frieren.entity.Task;
import com.frieren.security.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class CollaborativeSessionService {
    private static final String WEBSOCKET_PATH_TEMPLATE = "/ws/collaborative/%s";
    private static final long SESSION_GRACE_MINUTES = 5L;

    @Inject UserContext userContext;

    private final Map<UUID, RuntimeSessionState> runtimeStateBySession = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public StartCollaborativeSessionResponse startSession(CreateCollaborativeSessionRequest request) {
        if (request == null || request.projectId() == null || request.initialCommitHash() == null || request.initialCommitHash().isBlank()) {
            throw new IllegalArgumentException("projectId and initialCommitHash are required");
        }

        Project project = Project.findById(request.projectId());
        if (project == null || !project.projectActive) {
            throw new IllegalArgumentException("Project not found or inactive");
        }

        CollaborativeSession session = new CollaborativeSession();
        session.setId(UUID.randomUUID());
        session.setProject(project);
        if (request.taskId() != null) {
            Task task = Task.findById(request.taskId());
            if (task == null) {
                throw new IllegalArgumentException("Task not found");
            }
            session.setTask(task);
        }

        var now = OffsetDateTime.now();
        session.setInitiatedBy(userContext.getUserId());
        session.setSessionPasscode(generatePasscode());
        session.setStatusActive(true);
        session.setStartedAt(now);
        session.setLastHeartbeatAt(now);
        session.setInitialCommitHash(request.initialCommitHash());
        session.persist();

        joinInternal(session, userContext.getUserId(), now);
        runtimeStateBySession.putIfAbsent(session.getId(), new RuntimeSessionState());

        return new StartCollaborativeSessionResponse(
                session.getId(),
                session.getSessionPasscode(),
                WEBSOCKET_PATH_TEMPLATE.formatted(session.getId())
        );
    }

    public List<CollaborativeSessionSummaryResponse> listActiveSessions() {
        return CollaborativeSession.<CollaborativeSession>list("statusActive", true)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public JoinCollaborativeSessionResponse joinSession(UUID sessionId, String passcode) {
        CollaborativeSession session = getActiveSessionOrThrow(sessionId);
        if (passcode == null || !passcode.equals(session.getSessionPasscode())) {
            throw new SecurityException("Invalid session passcode");
        }

        joinInternal(session, userContext.getUserId(), OffsetDateTime.now());
        cancelScheduledClosure(sessionId);
        touchSession(sessionId);

        return new JoinCollaborativeSessionResponse(
                session.getId(),
                WEBSOCKET_PATH_TEMPLATE.formatted(session.getId())
        );
    }

    @Transactional
    public void leaveSession(UUID sessionId) {
        SessionParticipantId id = new SessionParticipantId();
        id.setSessionId(sessionId);
        id.setUserId(userContext.getUserId());

        SessionParticipant participant = SessionParticipant.findById(id);
        if (participant != null && participant.getLeftAt() == null) {
            participant.setLeftAt(OffsetDateTime.now());
        }

        scheduleClosureIfEmpty(sessionId);
    }

    public boolean isActiveParticipant(UUID sessionId, UUID userId) {
        CollaborativeSession session = CollaborativeSession.findById(sessionId);
        if (session == null || !Boolean.TRUE.equals(session.getStatusActive())) {
            return false;
        }

        SessionParticipant participant = SessionParticipant.findById(participantId(sessionId, userId));
        return participant != null && participant.getLeftAt() == null;
    }

    public SessionSnapshot lock(UUID sessionId, UUID userId) {
        RuntimeSessionState state = state(sessionId);
        synchronized (state) {
            if (state.lockOwner != null && !state.lockOwner.equals(userId)) {
                throw new IllegalStateException("Session is locked by another participant");
            }
            state.lockOwner = userId;
            touchSession(sessionId);
            return state.snapshot();
        }
    }

    public SessionSnapshot unlock(UUID sessionId, UUID userId) {
        RuntimeSessionState state = state(sessionId);
        synchronized (state) {
            if (userId.equals(state.lockOwner)) {
                state.lockOwner = null;
            }
            touchSession(sessionId);
            return state.snapshot();
        }
    }

    public SessionSnapshot updateContent(UUID sessionId, UUID userId, String content) {
        RuntimeSessionState state = state(sessionId);
        synchronized (state) {
            if (state.lockOwner != null && !state.lockOwner.equals(userId)) {
                throw new IllegalStateException("Session is locked by another participant");
            }
            state.content.setLength(0);
            if (content != null) {
                state.content.append(content);
            }
            touchSession(sessionId);
            return state.snapshot();
        }
    }

    public SessionSnapshot currentSnapshot(UUID sessionId) {
        return state(sessionId).snapshot();
    }

    public void onSocketDisconnected(UUID sessionId, UUID userId) {
        SessionParticipant participant = SessionParticipant.findById(participantId(sessionId, userId));
        if (participant != null && participant.getLeftAt() == null) {
            leaveUser(sessionId, userId);
        }
    }

    @Transactional
    void closeSessionIfStillEmpty(UUID sessionId) {
        CollaborativeSession session = CollaborativeSession.findById(sessionId);
        if (session == null || !Boolean.TRUE.equals(session.getStatusActive())) {
            return;
        }

        if (countActiveParticipants(sessionId) > 0) {
            return;
        }

        var now = OffsetDateTime.now();
        session.setStatusActive(false);
        session.setEndedAt(now);
        session.setLastHeartbeatAt(now);
        runtimeStateBySession.remove(sessionId);
    }

    private CollaborativeSessionSummaryResponse toSummary(CollaborativeSession session) {
        return new CollaborativeSessionSummaryResponse(
                session.getId(),
                session.getProject().id,
                session.getTask() != null ? session.getTask().id : null,
                session.getInitiatedBy(),
                session.getInitialCommitHash(),
                session.getStartedAt(),
                WEBSOCKET_PATH_TEMPLATE.formatted(session.getId())
        );
    }

    @Transactional
    protected void leaveUser(UUID sessionId, UUID userId) {
        SessionParticipant participant = SessionParticipant.findById(participantId(sessionId, userId));
        if (participant != null && participant.getLeftAt() == null) {
            participant.setLeftAt(OffsetDateTime.now());
        }
        scheduleClosureIfEmpty(sessionId);
    }

    private RuntimeSessionState state(UUID sessionId) {
        CollaborativeSession session = getActiveSessionOrThrow(sessionId);
        return runtimeStateBySession.computeIfAbsent(session.getId(), ignored -> new RuntimeSessionState());
    }

    @Transactional
    protected void touchSession(UUID sessionId) {
        CollaborativeSession session = getActiveSessionOrThrow(sessionId);
        session.setLastHeartbeatAt(OffsetDateTime.now());
    }

    private void joinInternal(CollaborativeSession session, UUID userId, OffsetDateTime now) {
        SessionParticipant participant = SessionParticipant.findById(participantId(session.getId(), userId));
        if (participant == null) {
            participant = new SessionParticipant();
            participant.setId(participantId(session.getId(), userId));
            participant.setCollaborativeSession(session);
            participant.setJoinedAt(now);
            participant.setLeftAt(null);
            participant.persist();
            return;
        }

        participant.setJoinedAt(now);
        participant.setLeftAt(null);
    }

    private void scheduleClosureIfEmpty(UUID sessionId) {
        if (countActiveParticipants(sessionId) > 0) {
            return;
        }

        RuntimeSessionState state = runtimeStateBySession.computeIfAbsent(sessionId, ignored -> new RuntimeSessionState());
        synchronized (state) {
            if (state.closeFuture != null && !state.closeFuture.isDone()) {
                return;
            }
            state.closeFuture = scheduler.schedule(
                    () -> closeSessionIfStillEmpty(sessionId),
                    SESSION_GRACE_MINUTES,
                    TimeUnit.MINUTES
            );
        }
    }

    private void cancelScheduledClosure(UUID sessionId) {
        RuntimeSessionState state = runtimeStateBySession.get(sessionId);
        if (state == null) {
            return;
        }

        synchronized (state) {
            if (state.closeFuture != null && !state.closeFuture.isDone()) {
                state.closeFuture.cancel(false);
            }
            state.closeFuture = null;
        }
    }

    private long countActiveParticipants(UUID sessionId) {
        return SessionParticipant.count("id.sessionId = ?1 and leftAt is null", sessionId);
    }

    private CollaborativeSession getActiveSessionOrThrow(UUID sessionId) {
        CollaborativeSession session = CollaborativeSession.findById(sessionId);
        if (session == null || !Boolean.TRUE.equals(session.getStatusActive())) {
            throw new IllegalArgumentException("Session not found or inactive");
        }
        return session;
    }

    private SessionParticipantId participantId(UUID sessionId, UUID userId) {
        SessionParticipantId id = new SessionParticipantId();
        id.setSessionId(sessionId);
        id.setUserId(userId);
        return id;
    }

    private String generatePasscode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder passcode = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            passcode.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return passcode.toString();
    }

    public record SessionSnapshot(String content, UUID lockOwner) {
    }

    private static final class RuntimeSessionState {
        private final StringBuilder content = new StringBuilder();
        private UUID lockOwner;
        private ScheduledFuture<?> closeFuture;

        private SessionSnapshot snapshot() {
            return new SessionSnapshot(content.toString(), lockOwner);
        }
    }
}
