package com.frieren.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateRepoRequest(
    String name,
    String description,
    @JsonProperty("private") boolean isPrivate
) {}
