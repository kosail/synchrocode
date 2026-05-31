package com.frieren.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        Short typeId,
        String typeName,
        String title,
        String body,
        String relatedEntity,
        UUID relatedId,
        Boolean isRead,
        OffsetDateTime createdAt,
        OffsetDateTime readAt
) {
}
