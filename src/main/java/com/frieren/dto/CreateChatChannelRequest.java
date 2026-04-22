package com.frieren.dto;

import java.util.UUID;

public record CreateChatChannelRequest(
        UUID projectId,
        String name
) {
}