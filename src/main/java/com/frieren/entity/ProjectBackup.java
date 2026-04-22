package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_backup")
public class ProjectBackup extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @NotNull
    @Column(name = "backup_file_url", nullable = false, length = Integer.MAX_VALUE)
    private String backupFileUrl;

    @NotNull
    @Column(name = "backup_file_size_bytes", nullable = false)
    private Long backupFileSizeBytes;

    @Column(name = "generated_by")
    private UUID generatedBy;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "is_archival", nullable = false)
    private Boolean isArchival;

    @Column(name = "restored_at")
    private OffsetDateTime restoredAt;

    @Column(name = "restored_by")
    private UUID restoredBy;

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

    public String getBackupFileUrl() {
        return backupFileUrl;
    }

    public void setBackupFileUrl(String backupFileUrl) {
        this.backupFileUrl = backupFileUrl;
    }

    public Long getBackupFileSizeBytes() {
        return backupFileSizeBytes;
    }

    public void setBackupFileSizeBytes(Long backupFileSizeBytes) {
        this.backupFileSizeBytes = backupFileSizeBytes;
    }

    public UUID getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(UUID generatedBy) {
        this.generatedBy = generatedBy;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Boolean getIsArchival() {
        return isArchival;
    }

    public void setIsArchival(Boolean isArchival) {
        this.isArchival = isArchival;
    }

    public OffsetDateTime getRestoredAt() {
        return restoredAt;
    }

    public void setRestoredAt(OffsetDateTime restoredAt) {
        this.restoredAt = restoredAt;
    }

    public UUID getRestoredBy() {
        return restoredBy;
    }

    public void setRestoredBy(UUID restoredBy) {
        this.restoredBy = restoredBy;
    }

}