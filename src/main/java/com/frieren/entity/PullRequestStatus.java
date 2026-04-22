package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "pull_request_status", uniqueConstraints = {@UniqueConstraint(name = "PullRequestStatus_name_key",
        columnNames = {"name"})})
public class PullRequestStatus extends PanacheEntityBase {
    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Size(max = 50)
    @NotNull
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    public Short getId() {
        return id;
    }

    public void setId(Short id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}