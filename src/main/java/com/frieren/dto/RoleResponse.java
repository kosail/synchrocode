package com.frieren.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RoleResponse(
    Short id,
    String name,
    String description,
    Map<String, List<String>> permissions,
    UUID organizationId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
