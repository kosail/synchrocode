package com.frieren.dto;

import java.util.UUID;

public record CreateCollaborativeSessionRequest(
        UUID projectId,
        UUID taskId,
        String initialCommitHash
) {
}
