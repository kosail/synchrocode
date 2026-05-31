package com.frieren.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.frieren.entity.UserProfile;
import com.frieren.security.UserContext;
import com.frieren.security.models.Roles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class UserService {
    @Inject
    UserContext userContext;

    @Inject
    SupabaseAdminService supabaseAdmin;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Invita a un nuevo usuario a la organización del admin.
     * Supabase envía un email con link para establecer contraseña.
     */
    public JsonNode invite(String email, String name, String role, String redirectTo) {
        requireAdmin();

        UUID orgId = userContext.getOrganizationId();
        if (orgId == null) throw new IllegalStateException("El administrador no tiene organización asignada");

        return supabaseAdmin.inviteUser(email, name, role, orgId.toString(), redirectTo);
    }

    /**
     * Lista los usuarios que pertenecen a la organización del admin.
     */
    public ArrayNode listByOrganization() {
        requireAdmin();

        UUID orgId = userContext.getOrganizationId();
        if (orgId == null) throw new IllegalStateException("El administrador no tiene organización asignada");

        JsonNode response = supabaseAdmin.listUsers(1, 1000);
        JsonNode users = response.get("users");

        ArrayNode filtered = mapper.createArrayNode();
        if (users != null && users.isArray()) {
            for (JsonNode user : users) {
                String userOrgId = extractOrgId(user);
                if (orgId.toString().equals(userOrgId)) {
                    filtered.add(buildUserResponse(user));
                }
            }
        }

        return filtered;
    }

    /**
     * Obtiene un usuario por ID, validando que pertenezca a la misma organización.
     */
    public ObjectNode get(UUID userId) {
        boolean isSelf = userId.equals(userContext.getUserId());

        if (!isSelf) {
            requireAdmin();
        }

        UUID orgId = userContext.getOrganizationId();
        JsonNode user = supabaseAdmin.getUser(userId.toString());

        String userOrgId = extractOrgId(user);
        if (!orgId.toString().equals(userOrgId)) {
            throw new IllegalArgumentException("El usuario no pertenece a tu organización");
        }

        return buildUserResponse(user);
    }

    /**
     * Permite a un usuario actualizar su propio nombre o a un admin actualizar el de alguien de su organización.
     */
    public ObjectNode updateName(UUID userId, String newName) {
        boolean isSelf = userId.equals(userContext.getUserId());

        if (!isSelf) {
            requireAdmin();
            UUID orgId = userContext.getOrganizationId();
            JsonNode user = supabaseAdmin.getUser(userId.toString());
            String userOrgId = extractOrgId(user);
            if (orgId == null || !orgId.toString().equals(userOrgId)) {
                throw new IllegalArgumentException("El usuario no pertenece a tu organización o no tienes permiso");
            }
        }

        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("name", newName);

        JsonNode updated = supabaseAdmin.updateUser(userId.toString(), metadata);
        return buildUserResponse(updated);
    }

    /**
     * Actualiza el rol de un usuario.
     */
    public ObjectNode updateRole(UUID userId, String newRole) {
        requireAdmin();

        UUID orgId = userContext.getOrganizationId();
        JsonNode user = supabaseAdmin.getUser(userId.toString());

        String userOrgId = extractOrgId(user);
        if (!orgId.toString().equals(userOrgId)) {
            throw new IllegalArgumentException("El usuario no pertenece a tu organización");
        }

        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("role", newRole);

        JsonNode updated = supabaseAdmin.updateUser(userId.toString(), metadata);
        return buildUserResponse(updated);
    }

    /**
     * Elimina un usuario de la organización.
     */
    public void delete(UUID userId) {
        requireAdmin();

        UUID orgId = userContext.getOrganizationId();
        JsonNode user = supabaseAdmin.getUser(userId.toString());

        String userOrgId = extractOrgId(user);
        if (!orgId.toString().equals(userOrgId)) {
            throw new IllegalArgumentException("El usuario no pertenece a tu organización");
        }

        // No permitir que el admin se elimine a sí mismo
        if (userId.equals(userContext.getUserId())) {
            throw new IllegalArgumentException("No puedes eliminarte a ti mismo");
        }

        supabaseAdmin.deleteUser(userId.toString());
    }

    /**
     * Obtiene solo el nombre del usuario sin validaciones de seguridad.
     * Uso interno para el sistema.
     */
    public String getNameUnsafe(UUID userId) {
        try {
            JsonNode user = supabaseAdmin.getUser(userId.toString());
            return extractName(user);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtiene el GitHub username desde user_profile o desde los metadatos de Supabase Auth.
     * Si lo encuentra en Supabase Auth, lo persiste en user_profile para siguientes usos.
     */
    @Transactional
    public String getGithubUsernameUnsafe(UUID userId) {
        try {
            UserProfile profile = UserProfile.findById(userId);
            if (profile != null && profile.getGithubUsername() != null && !profile.getGithubUsername().isBlank()) {
                return profile.getGithubUsername();
            }

            JsonNode user = supabaseAdmin.getUser(userId.toString());
            String githubUsername = extractGithubUsername(user);
            if (githubUsername != null && !githubUsername.isBlank() && profile != null) {
                profile.setGithubUsername(githubUsername);
                profile.setUpdatedAt(Instant.now());
            }
            return githubUsername;
        } catch (Exception e) {
            return null;
        }
    }

    public void setGithubUsernameUnsafe(UUID userId, String githubUsername) {
        if (githubUsername == null || githubUsername.isBlank()) {
            return;
        }

        UserProfile profile = UserProfile.findById(userId);
        if (profile != null) {
            profile.setGithubUsername(githubUsername.trim());
            profile.setUpdatedAt(Instant.now());
        }

        try {
            JsonNode user = supabaseAdmin.getUser(userId.toString());
            ObjectNode metadata = mapper.createObjectNode();

            String name = extractName(user);
            if (name != null) metadata.put("name", name);

            String role = extractRole(user);
            if (role != null) metadata.put("role", role);

            String orgId = extractOrgId(user);
            if (orgId != null) metadata.put("organizationId", orgId);

            metadata.put("githubUsername", githubUsername.trim());
            metadata.put("github_username", githubUsername.trim());
            supabaseAdmin.updateUser(userId.toString(), metadata);
        } catch (Exception ignored) {
            // The local user_profile update is enough when it exists. Metadata sync is best effort.
        }
    }

    private void requireAdmin() {
        if (!Roles.ADMIN.equals(userContext.role())) {
            throw new SecurityException("Solo los administradores pueden gestionar usuarios");
        }
    }

    /**
     * Extrae el organizationId del user_metadata de Supabase.
     * Busca en ambos niveles: directo y dentro de raw_user_meta_data.
     */
    private String extractOrgId(JsonNode user) {
        JsonNode metadata = user.get("user_metadata");
        if (metadata == null) return null;

        // Buscar en nivel directo (updateUser)
        JsonNode orgId = metadata.get("organizationId");
        if (orgId != null && !orgId.isNull()) return orgId.asText();

        // Buscar en raw_user_meta_data (signUp / Admin API invite)
        JsonNode raw = metadata.get("raw_user_meta_data");
        if (raw != null) {
            orgId = raw.get("organizationId");
            if (orgId != null && !orgId.isNull()) return orgId.asText();
        }

        return null;
    }

    private String extractRole(JsonNode user) {
        JsonNode metadata = user.get("user_metadata");
        if (metadata == null) return Roles.PROGRAMMER;

        JsonNode role = metadata.get("role");
        if (role != null && !role.isNull()) return role.asText();

        JsonNode raw = metadata.get("raw_user_meta_data");
        if (raw != null) {
            role = raw.get("role");
            if (role != null && !role.isNull()) return role.asText();
        }

        return Roles.PROGRAMMER;
    }

    private String extractName(JsonNode user) {
        JsonNode metadata = user.get("user_metadata");
        if (metadata == null) return null;

        JsonNode name = metadata.get("name");
        if (name != null && !name.isNull()) return name.asText();

        JsonNode raw = metadata.get("raw_user_meta_data");
        if (raw != null) {
            name = raw.get("name");
            if (name != null && !name.isNull()) return name.asText();
        }

        return null;
    }

    private String extractGithubUsername(JsonNode user) {
        JsonNode metadata = user.get("user_metadata");
        String fromMetadata = extractGithubUsernameFromNode(metadata);
        if (fromMetadata != null) return fromMetadata;

        JsonNode identities = user.get("identities");
        if (identities != null && identities.isArray()) {
            for (JsonNode identity : identities) {
                String provider = identity.path("provider").asText("");
                if (!"github".equalsIgnoreCase(provider)) continue;

                String fromIdentity = extractGithubUsernameFromNode(identity.get("identity_data"));
                if (fromIdentity != null) return fromIdentity;
            }
        }

        JsonNode appMetadata = user.get("app_metadata");
        JsonNode providers = appMetadata != null ? appMetadata.get("providers") : null;
        if (providers != null && providers.isArray()) {
            for (JsonNode provider : providers) {
                if ("github".equalsIgnoreCase(provider.asText())) {
                    return extractGithubUsernameFromNode(metadata);
                }
            }
        }

        return null;
    }

    private String extractGithubUsernameFromNode(JsonNode node) {
        if (node == null || node.isNull()) return null;

        for (String key : new String[]{"user_name", "preferred_username", "github_username", "githubUsername", "login", "nickname"}) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }

        JsonNode raw = node.get("raw_user_meta_data");
        if (raw != null) {
            String fromRaw = extractGithubUsernameFromNode(raw);
            if (fromRaw != null) return fromRaw;
        }

        return null;
    }

    /**
     * Construye un objeto JSON limpio con los datos del usuario para el frontend.
     */
    private ObjectNode buildUserResponse(JsonNode user) {
        ObjectNode result = mapper.createObjectNode();
        result.put("id", user.get("id").asText());
        result.put("email", user.get("email").asText());
        result.put("name", extractName(user));
        result.put("role", extractRole(user));
        String githubUsername = extractGithubUsername(user);
        if (githubUsername != null) result.put("githubUsername", githubUsername);

        JsonNode createdAt = user.get("created_at");
        if (createdAt != null) result.put("createdAt", createdAt.asText());

        JsonNode lastSignIn = user.get("last_sign_in_at");
        if (lastSignIn != null && !lastSignIn.isNull()) {
            result.put("lastSignInAt", lastSignIn.asText());
        }

        return result;
    }
}
