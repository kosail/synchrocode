package com.frieren.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CollaborativeSessionSummaryResponse(
        UUID sessionId,
        UUID projectId,
        UUID taskId,
        UUID initiatedBy,
        String initialCommitHash,
        OffsetDateTime startedAt,
        String websocketPath
) {
}
