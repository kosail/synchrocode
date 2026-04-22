package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "collaborative_session", indexes = {@Index(name = "idx_Session_project_active",
        columnList = "project_id, status_active")})
public class CollaborativeSession extends PanacheEntityBase {
    @Id
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "task_id")
    private Task task;

    @NotNull
    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Size(max = 20)
    @NotNull
    @Column(name = "session_passcode", nullable = false, length = 20)
    private String sessionPasscode;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "status_active", nullable = false)
    private Boolean statusActive;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "last_heartbeat_at", nullable = false)
    private OffsetDateTime lastHeartbeatAt;

    @NotNull
    @Column(name = "initial_commit_hash", nullable = false, length = Integer.MAX_VALUE)
    private String initialCommitHash;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(UUID initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public String getSessionPasscode() {
        return sessionPasscode;
    }

    public void setSessionPasscode(String sessionPasscode) {
        this.sessionPasscode = sessionPasscode;
    }

    public Boolean getStatusActive() {
        return statusActive;
    }

    public void setStatusActive(Boolean statusActive) {
        this.statusActive = statusActive;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public OffsetDateTime getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(OffsetDateTime lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public String getInitialCommitHash() {
        return initialCommitHash;
    }

    public void setInitialCommitHash(String initialCommitHash) {
        this.initialCommitHash = initialCommitHash;
    }

}