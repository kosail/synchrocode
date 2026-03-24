package com.frieren.security;

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

    public String role() {
        Map<String, Object> metadata = jwt.getClaim("user_metadata");

        if (metadata != null && metadata.containsKey("role")) {
            return metadata.get("role").toString();
        }

        return Roles.PROGRAMMER;
    }

    public String getEmail() {
        return jwt.getClaim("email");
    }
}
