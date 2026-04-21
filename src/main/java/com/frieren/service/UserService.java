package com.frieren.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.frieren.security.UserContext;
import com.frieren.security.models.Roles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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

    /**
     * Construye un objeto JSON limpio con los datos del usuario para el frontend.
     */
    private ObjectNode buildUserResponse(JsonNode user) {
        ObjectNode result = mapper.createObjectNode();
        result.put("id", user.get("id").asText());
        result.put("email", user.get("email").asText());
        result.put("name", extractName(user));
        result.put("role", extractRole(user));

        JsonNode createdAt = user.get("created_at");
        if (createdAt != null) result.put("createdAt", createdAt.asText());

        JsonNode lastSignIn = user.get("last_sign_in_at");
        if (lastSignIn != null && !lastSignIn.isNull()) {
            result.put("lastSignInAt", lastSignIn.asText());
        }

        return result;
    }
}
