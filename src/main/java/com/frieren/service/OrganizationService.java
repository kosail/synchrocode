package com.frieren.service;

import com.frieren.entity.Organization;
import com.frieren.security.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static java.util.UUID.randomUUID;

@ApplicationScoped
public class OrganizationService {
    @Inject
    UserContext userContext;

    /**
     * Obtiene todas las organizaciones/empresas/compañías.
     * @return Lista de todas las organizaciones registradas.
     */
    public List<Organization> getAll() {
        return Organization.listAll();
    }

    /**
     * Obtiene una organización por su ID unico.
     *
     * @param id El UUID de la organización a buscar
     * @return La organización encontrada o null si no existe
     */
    public Organization get(UUID id) {
            return Organization.findById(id);
    }

    /**
     * Crea una nueva organización.
     *
     * @param organization La organización a crear.
     * @return La organización creada.
     */
    @Transactional
    public Organization create(Organization organization) {
        if (organization.id != null && Organization.findById(organization.id) != null) {
            throw new IllegalArgumentException("La organización ya existe");
        }

        if (organization.name == null || organization.name.isBlank()) {
            throw new IllegalArgumentException("El nombre de la organización es obligatorio");
        }

        var now = OffsetDateTime.now();
        var uuid = organization.id != null ? organization.id : randomUUID();

        // En caso de colisión, generamos otro UUID.
        while (Organization.findById(uuid) != null) {
            uuid = randomUUID();
        }

        organization.id = uuid;
        organization.createdAt = now;
        organization.updatedAt = now;

        organization.persist();
        return organization;
    }

    /**
     * Actualiza una organización existente.
     *
     * @param id El UUID de la organización a actualizar.
     * @param update La organización con los nuevos datos.
     * @return La organización actualizada.
     */
    @Transactional
    public Organization update(UUID id, Organization update) {
        Organization existing = Organization.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("La organización no existe");
        }

        if (update.name != null && !update.name.isBlank()) {
            existing.name = update.name;
        }

        existing.updatedAt = OffsetDateTime.now();

        return existing;
    }

    /**
     * Elimina una organización por su ID.
     *
     * @param id El UUID de la organización a eliminar.
     * @return true si se eliminó correctamente, false en caso contrario.
     */
    @Transactional
    public boolean delete(UUID id) {
        Organization existing = Organization.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("La organización no existe");
        }

        return Organization.deleteById(id);
    }
}
