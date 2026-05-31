package com.frieren.dto;

public record GitHubStatsResponse(
        long commits,
        long pullRequests,
        long linked
) {}
