package com.frieren.dto;

import java.util.List;
import java.util.Map;

public record RoleRequest(
    String name,
    String description,
    Map<String, List<String>> permissions
) {}
