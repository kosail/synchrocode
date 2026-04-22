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
@Table(name = "github_repository_link", uniqueConstraints = {@UniqueConstraint(name = "uq_GitHubRepositoryLink_repo_active",
        columnNames = {"github_repo_id"})})
public class GithubRepositoryLink extends PanacheEntityBase {
    @Id
    @ColumnDefault("gen_random_uuid()")
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Size(max = 255)
    @NotNull
    @Column(name = "github_repo_id", nullable = false)
    private String githubRepoId;

    @Size(max = 255)
    @NotNull
    @Column(name = "github_repo_full_name", nullable = false)
    private String githubRepoFullName;

    @Size(max = 255)
    @NotNull
    @Column(name = "github_installation_id", nullable = false)
    private String githubInstallationId;

    @Size(max = 255)
    @NotNull
    @Column(name = "webhook_secret", nullable = false)
    private String webhookSecret;

    @Column(name = "linked_by")
    private UUID linkedBy;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt;

    @Column(name = "unlinked_at")
    private OffsetDateTime unlinkedAt;

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

    public String getGithubRepoId() {
        return githubRepoId;
    }

    public void setGithubRepoId(String githubRepoId) {
        this.githubRepoId = githubRepoId;
    }

    public String getGithubRepoFullName() {
        return githubRepoFullName;
    }

    public void setGithubRepoFullName(String githubRepoFullName) {
        this.githubRepoFullName = githubRepoFullName;
    }

    public String getGithubInstallationId() {
        return githubInstallationId;
    }

    public void setGithubInstallationId(String githubInstallationId) {
        this.githubInstallationId = githubInstallationId;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public UUID getLinkedBy() {
        return linkedBy;
    }

    public void setLinkedBy(UUID linkedBy) {
        this.linkedBy = linkedBy;
    }

    public OffsetDateTime getLinkedAt() {
        return linkedAt;
    }

    public void setLinkedAt(OffsetDateTime linkedAt) {
        this.linkedAt = linkedAt;
    }

    public OffsetDateTime getUnlinkedAt() {
        return unlinkedAt;
    }

    public void setUnlinkedAt(OffsetDateTime unlinkedAt) {
        this.unlinkedAt = unlinkedAt;
    }

}