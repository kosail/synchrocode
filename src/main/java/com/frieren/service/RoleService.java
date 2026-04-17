package com.frieren.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.frieren.dto.RoleRequest;
import com.frieren.dto.RoleResponse;
import com.frieren.entity.Organization;
import com.frieren.entity.Role;
import com.frieren.repository.RoleRepository;
import com.frieren.security.UserContext;
import com.frieren.security.models.Roles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class RoleService {

    @Inject
    RoleRepository roleRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    UserContext userContext;

    @Inject
    UserService userService;

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        requireAdmin();
        if (request.name() == null || request.name().trim().isEmpty()) {
            throw new WebApplicationException("El nombre del rol es obligatorio.", Response.Status.BAD_REQUEST);
        }

        UUID orgId = userContext.getOrganizationId();
        if (orgId == null) {
            throw new WebApplicationException("No estás asignado a ninguna organización.", Response.Status.FORBIDDEN);
        }

        if (roleRepository.findByNameInOrgOrGlobal(request.name(), orgId).isPresent()) {
            throw new WebApplicationException("Ya existe un rol con ese nombre en su organización o es un rol reservado del sistema.", Response.Status.CONFLICT);
        }

        Role role = new Role();
        role.setName(request.name().trim());
        role.setDescription(request.description());
        if (request.permissions() != null) {
            role.setPermissions(request.permissions());
        }

        Organization org = entityManager.getReference(Organization.class, orgId);
        role.setOrganization(org);

        roleRepository.persist(role);
        return mapToResponse(role);
    }

    public List<RoleResponse> getAllRoles() {
        requireAdmin();
        UUID orgId = userContext.getOrganizationId();
        if (orgId == null) {
            throw new WebApplicationException("No estás asignado a ninguna organización.", Response.Status.FORBIDDEN);
        }

        return roleRepository.findRolesForOrganization(orgId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public RoleResponse getRoleById(Short id) {
        requireAdmin();
        Role role = roleRepository.findByIdOptional(id)
                .orElseThrow(() -> new WebApplicationException("Rol no encontrado", Response.Status.NOT_FOUND));

        UUID currentOrgId = userContext.getOrganizationId();
        if (role.getOrganization() != null && !role.getOrganization().id.equals(currentOrgId)) {
            throw new WebApplicationException("No tienes permiso sobre este rol.", Response.Status.FORBIDDEN);
        }

        return mapToResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(Short id, RoleRequest request) {
        requireAdmin();
        if (request.name() == null || request.name().trim().isEmpty()) {
            throw new WebApplicationException("El nombre del rol es obligatorio.", Response.Status.BAD_REQUEST);
        }

        Role role = roleRepository.findByIdOptional(id)
                .orElseThrow(() -> new WebApplicationException("Rol no encontrado", Response.Status.NOT_FOUND));

        UUID currentOrgId = userContext.getOrganizationId();

        if (role.getOrganization() == null) {
            throw new WebApplicationException("No puedes modificar los roles por defecto del sistema.", Response.Status.FORBIDDEN);
        }

        if (!role.getOrganization().id.equals(currentOrgId)) {
            throw new WebApplicationException("No tienes permiso sobre este rol.", Response.Status.FORBIDDEN);
        }

        roleRepository.findByNameInOrgOrGlobal(request.name(), currentOrgId).ifPresent(existingRole -> {
            if (!existingRole.getId().equals(id)) {
                throw new WebApplicationException("Ya existe un rol con ese nombre en su organización o es un rol reservado del sistema.", Response.Status.CONFLICT);
            }
        });

        role.setName(request.name().trim());
        role.setDescription(request.description());
        if (request.permissions() != null) {
            role.setPermissions(request.permissions());
        }

        return mapToResponse(role);
    }

    @Transactional
    public void deleteRole(Short id) {
        requireAdmin();
        Role role = roleRepository.findByIdOptional(id)
                .orElseThrow(() -> new WebApplicationException("Rol no encontrado", Response.Status.NOT_FOUND));

        UUID currentOrgId = userContext.getOrganizationId();

        if (role.getOrganization() == null) {
            throw new WebApplicationException("No puedes eliminar los roles por defecto del sistema.", Response.Status.FORBIDDEN);
        }

        if (!role.getOrganization().id.equals(currentOrgId)) {
            throw new WebApplicationException("No tienes permiso sobre este rol.", Response.Status.FORBIDDEN);
        }

        ArrayNode users = userService.listByOrganization();
        long activeUsersCount = 0;
        if (users != null) {
            for (JsonNode user : users) {
                JsonNode roleNode = user.get("role");
                if (roleNode != null && role.getName().equalsIgnoreCase(roleNode.asText())) {
                    activeUsersCount++;
                }
            }
        }

        if (activeUsersCount > 0) {
            throw new WebApplicationException(
                    "Este rol está asignado a " + activeUsersCount + " usuario(s) activo(s). Reasígnalos antes de eliminar.", 
                    Response.Status.CONFLICT);
        }

        roleRepository.delete(role);
    }

    private void requireAdmin() {
        if (!Roles.ADMIN.equals(userContext.role())) {
            throw new SecurityException("Solo los administradores pueden gestionar roles");
        }
    }

    private RoleResponse mapToResponse(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getPermissions(),
                role.getOrganization() != null ? role.getOrganization().id : null,
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }
}
