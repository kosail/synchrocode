package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

class ProjectTeamId implements Serializable {
    public UUID projectId;
    public UUID userId;

    public ProjectTeamId() {}

    public ProjectTeamId(UUID projectId, UUID userId) {
        this.projectId = projectId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectTeamId that = (ProjectTeamId) o;
        return Objects.equals(projectId, that.projectId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, userId);
    }
}

@Entity
@Table(name = "project_team")
@IdClass(ProjectTeamId.class)
public class ProjectTeam extends PanacheEntityBase {
    @Id
    public UUID projectId;

    @Id
    public UUID userId;

    public OffsetDateTime joinedAt;
}
