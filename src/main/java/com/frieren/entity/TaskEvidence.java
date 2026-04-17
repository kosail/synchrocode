package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
public class TaskEvidence extends PanacheEntityBase {
    @Id public UUID id;
    public UUID taskId;
    public String fileName;
    public String fileUrl;
    public Long fileSizeBytes;
    public UUID uploadedBy;
    public OffsetDateTime uploadedAt;
}
