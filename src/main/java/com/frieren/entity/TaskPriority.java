package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class TaskPriority extends PanacheEntityBase {
    @Id public Short id;
    public String name;
}
