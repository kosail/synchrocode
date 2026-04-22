package com.frieren.dto;

import java.util.UUID;

public record StartCollaborativeSessionResponse(
        UUID sessionId,
        String passcode,
        String websocketPath
) {
}
