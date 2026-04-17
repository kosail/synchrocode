package com.frieren.security;

import com.frieren.security.models.Roles;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Map;
import java.util.UUID;

@RequestScoped
public class UserContext {
    @Inject JsonWebToken jwt;

    public UUID getUserId() {
        return UUID.fromString(jwt.getSubject());
    }

    // Holy god I hate to use Object, but tbh I have no fucking idea how to really do this in a safer way that actually works
    public UUID getOrganizationId() {
        Map<String, Object> metadata = jwt.getClaim("user_metadata");
        if (metadata == null) return null;

        // Buscar en user_metadata directo (donde updateUser lo pone)
        Object orgId = metadata.get("organizationId");

        // Si no está, buscar en raw_user_meta_data (donde signUp lo pone)
        if (orgId == null) {
            Object raw = metadata.get("raw_user_meta_data");
            if (raw instanceof Map<?, ?> rawMetadata) {
                orgId = rawMetadata.get("organizationId");
            }
        }

        if (orgId == null) return null;

        return UUID.fromString(orgId.toString().replace("\"", ""));
    }

    public String role() {
        Map<String, Object> metadata = jwt.getClaim("user_metadata");
        if (metadata == null) return Roles.PROGRAMMER;

        // Buscar en user_metadata directo (donde updateUser lo pone)
        Object role = metadata.get("role");

        // Si no está, buscar en raw_user_meta_data (donde signUp lo pone)
        if (role == null) {
            Object raw = metadata.get("raw_user_meta_data");
            if (raw instanceof Map<?, ?> rawMetadata) {
                role = rawMetadata.get("role");
            }
        }

        return role != null ? role.toString().replace("\"", "") : Roles.PROGRAMMER;
    }

    public String getEmail() {
        return jwt.getClaim("email");
    }
}
