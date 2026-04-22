package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "audit_log_action_type", uniqueConstraints = {@UniqueConstraint(name = "AuditLogActionType_name_key",
        columnNames = {"name"})})
public class AuditLogActionType extends PanacheEntityBase {
    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Size(max = 50)
    @NotNull
    @Column(name = "module", nullable = false, length = 50)
    private String module;

    @Size(max = 100)
    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = Integer.MAX_VALUE)
    private String description;

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}