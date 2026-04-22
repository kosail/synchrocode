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
@Table(name = "pull_request_link", indexes = {
        @Index(name = "idx_PullRequestLink_task_id",
                columnList = "task_id"),
        @Index(name = "idx_PullRequestLink_status_id",
                columnList = "status_id")}, uniqueConstraints = {@UniqueConstraint(name = "uq_PullRequestLink_pr_task",
        columnNames = {
                "pr_number",
                "task_id"})})
public class PullRequestLink extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @NotNull
    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Size(max = 255)
    @NotNull
    @Column(name = "pr_title", nullable = false)
    private String prTitle;

    @NotNull
    @Column(name = "pr_url", nullable = false, length = Integer.MAX_VALUE)
    private String prUrl;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @ColumnDefault("1")
    @JoinColumn(name = "status_id", nullable = false)
    private PullRequestStatus status;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "is_merged", nullable = false)
    private Boolean isMerged;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt;

    @Column(name = "merged_at")
    private OffsetDateTime mergedAt;

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

    public Integer getPrNumber() {
        return prNumber;
    }

    public void setPrNumber(Integer prNumber) {
        this.prNumber = prNumber;
    }

    public String getPrTitle() {
        return prTitle;
    }

    public void setPrTitle(String prTitle) {
        this.prTitle = prTitle;
    }

    public String getPrUrl() {
        return prUrl;
    }

    public void setPrUrl(String prUrl) {
        this.prUrl = prUrl;
    }

    public PullRequestStatus getStatus() {
        return status;
    }

    public void setStatus(PullRequestStatus status) {
        this.status = status;
    }

    public Boolean getIsMerged() {
        return isMerged;
    }

    public void setIsMerged(Boolean isMerged) {
        this.isMerged = isMerged;
    }

    public OffsetDateTime getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(OffsetDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }

    public OffsetDateTime getMergedAt() {
        return mergedAt;
    }

    public void setMergedAt(OffsetDateTime mergedAt) {
        this.mergedAt = mergedAt;
    }

}