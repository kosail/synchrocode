package com.frieren.dto;

public record GitHubCollaboratorResponse(
        String login,
        String avatarUrl,
        String htmlUrl,
        String permission
) {}
