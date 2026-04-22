package com.frieren.dto;

import java.util.UUID;

public record CreateChatChannelResponse(
        UUID channelId,
        UUID projectId,
        String name,
        String websocketPath
) {
}