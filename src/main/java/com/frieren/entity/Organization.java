package com.frieren.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
public class Organization extends PanacheEntityBase {
    @Id public UUID id;
    @Column(columnDefinition = "TEXT") public String name;
    public OffsetDateTime createdAt;
    public OffsetDateTime updatedAt;
}
