package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;

@Entity
@Table(name = "session_participant")
public class SessionParticipant extends PanacheEntityBase {
    @EmbeddedId
    private SessionParticipantId id;

    @MapsId("sessionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "session_id", nullable = false)
    private CollaborativeSession session;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    public SessionParticipantId getId() {
        return id;
    }

    public void setId(SessionParticipantId id) {
        this.id = id;
    }

    public CollaborativeSession getCollaborativeSession() {
        return session;
    }

    public void setCollaborativeSession(CollaborativeSession session) {
        this.session = session;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(OffsetDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public OffsetDateTime getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(OffsetDateTime leftAt) {
        this.leftAt = leftAt;
    }

}