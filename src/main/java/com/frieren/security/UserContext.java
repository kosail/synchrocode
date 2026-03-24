package com.frieren.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Map;
import java.util.UUID;

@RequestScoped
public class UserContext {
    @Inject
    JsonWebToken jwt;

    public UUID getUserId() {
        return UUID.fromString(jwt.getClaim("sub"));
    }

    public String role() {
        return jwt.getClaim("user_metadata") != null
                ? ((Map<String, Object>) jwt.getClaim("user_metadata")).get("role").toString()
                : "programmer";
    }

    public String getEmail() {
        return jwt.getClaim("email");
    }
}
