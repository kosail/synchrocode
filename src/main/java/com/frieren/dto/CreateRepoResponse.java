package com.frieren.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateRepoResponse(
    long id,
    @JsonProperty("html_url") String htmlUrl,
    @JsonProperty("full_name") String fullName
) {}
