package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
public class Task extends PanacheEntityBase {
    @Id public UUID id;
    
    @JsonProperty("project_id")
    public UUID projectId;
    
    @Column(nullable = true)
    @JsonProperty("jira_task_id")
    public String jiraTaskId;
    
    public String title;
    @Column(columnDefinition = "TEXT") public String description;
    
    @JsonProperty("status_id")
    public Short statusId;
    
    @JsonProperty("priority_id")
    public Short priorityId;
    
    @JsonProperty("assigned_to")
    public UUID assignedTo;
    
    @JsonProperty("due_date")
    public LocalDate dueDate;
    
    @JsonProperty("completed_at")
    public OffsetDateTime completedAt;
    
    @JsonProperty("created_by")
    public UUID createdBy;
    
    @JsonProperty("created_at")
    public OffsetDateTime createdAt;
    
    @JsonProperty("updated_at")
    public OffsetDateTime updatedAt;

    @Transient
    public List<TaskEvidence> evidence;
}
