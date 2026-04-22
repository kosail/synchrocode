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
@Table(name = "commit_link", indexes = {@Index(name = "idx_CommitLink_task_id",
        columnList = "task_id")}, uniqueConstraints = {@UniqueConstraint(name = "uq_CommitLink_sha_task",
        columnNames = {
                "commit_sha",
                "task_id"})})
public class CommitLink extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Size(max = 40)
    @NotNull
    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    @NotNull
    @Column(name = "commit_message", nullable = false, length = Integer.MAX_VALUE)
    private String commitMessage;

    @NotNull
    @Column(name = "commit_url", nullable = false, length = Integer.MAX_VALUE)
    private String commitUrl;

    @Size(max = 100)
    @NotNull
    @Column(name = "author_username", nullable = false, length = 100)
    private String authorUsername;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public String getCommitUrl() {
        return commitUrl;
    }

    public void setCommitUrl(String commitUrl) {
        this.commitUrl = commitUrl;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public void setAuthorUsername(String authorUsername) {
        this.authorUsername = authorUsername;
    }

    public OffsetDateTime getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(OffsetDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }

}