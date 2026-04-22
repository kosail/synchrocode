package com.frieren.dto;

import java.util.UUID;

public record JoinCollaborativeSessionResponse(
        UUID sessionId,
        String websocketPath
) {
}
