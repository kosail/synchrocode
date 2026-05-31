package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
public class Project extends PanacheEntityBase {
    @Id public UUID id;
    @Column(columnDefinition = "TEXT") public String name;
    @Column(columnDefinition = "TEXT") public String description;
    public UUID organizationId;
    public boolean projectActive;
    public LocalDate startDate;
    public LocalDate endDate;
    public UUID createdBy;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
    public OffsetDateTime archivedAt;
    @Column(columnDefinition = "TEXT") public String repoUrl;

    /** Transient: used only during project creation to pass GitHub usernames */
    @Transient
    public List<String> githubUsernames;
}
