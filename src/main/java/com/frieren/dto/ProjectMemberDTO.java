package com.frieren.dto;

import java.util.UUID;

public record ProjectMemberDTO(
    UUID userId,
    String name,
    String email,
    String role
) {}
