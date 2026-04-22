package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "notification_type", uniqueConstraints = {@UniqueConstraint(name = "NotificationType_name_key",
        columnNames = {"name"})})
public class NotificationType extends PanacheEntityBase {
    @Id
    @Column(name = "id", nullable = false)
    private Short id;

    @Size(max = 100)
    @NotNull
    @Column(name = "name", nullable = false, length = 100)
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