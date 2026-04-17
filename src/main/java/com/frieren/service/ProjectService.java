package com.frieren.service;

import com.frieren.entity.Project;
import com.frieren.entity.ProjectTeam;
import com.frieren.security.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static java.util.UUID.randomUUID;

@ApplicationScoped
public class ProjectService {
    @Inject
    UserContext userContext;

    // TODO: Añadir un filtrado de proyectos por la organización a la que pertenece el usuario,
    // pero eso luego ya que cree la tabla de Organization

    /**
     * Para obtener todos los proyectos de la organización del usuario.
     * @return Lista de todos los proyectos de la organización, incluyendo activos, no activos y archivados.
     */
    public List<Project> getAll() {
        UUID orgId = userContext.getOrganizationId();
        if (orgId == null) throw new IllegalStateException("Organización perteneciente al usuario no existe");
        return Project.list("organizationId", orgId);
    }

    /**
     * Para obtener todos los proyectos activos y no archivados de la organización del usuario.
     * @return Lista de todos los proyectos activos y no archivados de la organización.
     */
    public List<Project> getActive() {
        UUID orgId = userContext.getOrganizationId();
        if (orgId == null) throw new IllegalStateException("Organización perteneciente al usuario no existe");
        return Project.list("organizationId = ?1 AND projectActive = true AND archivedAt IS NULL", orgId);
    }

    /**
     * Para obtener todos los proyectos archivados/no activos de la organización del usuario.
     * @return Lista de todos los proyectos archivados, o null si no hay nada.
     */
    public List<Project> getArchived() {
        UUID orgId = userContext.getOrganizationId();
        if (orgId == null) throw new IllegalStateException("Organización perteneciente al usuario no existe");
        return Project.list("organizationId = ?1 AND archivedAt IS NOT NULL", orgId);
    }

    /**
     * Para obtener un proyecto buscando por ID, validando que pertenezca a la organización del usuario.
     * @param id Identificador/Primary key UUID del proyecto
     * @return El proyecto o null si no existe o no pertenece a la organización.
     */
    public Project get(UUID id) {
        Project project = Project.findById(id);
        if (project == null) return null;

        UUID orgId = userContext.getOrganizationId();
        if (orgId == null || !orgId.equals(project.organizationId)) {
            return null;
        }
        return project;
    }

    /**
     * Crea un nuevo proyecto
     * <p>
     * (El usuario que lo solicita queda registrado como el creador de dicho proyecto)
     * @param project Proyecto a crear.
     * @return El proyecto creado en caso exitoso. En caso de fallo, no devuelve nada y lanza una excepción.
     * @throws IllegalArgumentException Si el proyecto ya existe o los campos son invalidos.
     */
    @Transactional
    public Project create(Project project) {
        UUID orgId = userContext.getOrganizationId();
        if (orgId == null) throw new IllegalStateException("Organización perteneciente al usuario no existe");

        if (project.id != null && Project.findById(project.id) != null) {
            throw new IllegalArgumentException("El proyecto ya existe");
        }

        if (project.name == null || project.name.isBlank()) {
            throw new IllegalArgumentException("El nombre del proyecto es obligatorio");
        }

        var now = OffsetDateTime.now();
        var uuid = randomUUID();
        if (Project.findById(uuid) != null) uuid = randomUUID(); // edge case of collision, but still nice to have tho

        project.id = uuid;
        project.organizationId = orgId;
        project.projectActive = true;
        project.createdBy = userContext.getUserId();
        project.createdAt = now;
        project.updatedAt = now;

        project.persist();

        // Añadir automáticamente al creador como el primer miembro del equipo
        ProjectTeam creatorMember = new ProjectTeam();
        creatorMember.projectId = uuid;
        creatorMember.userId = userContext.getUserId();
        creatorMember.joinedAt = now;
        creatorMember.persist();

        return project;
    }

    /**
     * Actualiza un proyecto existente.
     * <p>
     * Esta función NO actualiza los campos de "createdBy" ni "createdAt" porque son inmutables.
     * <p>
     * No actualiza los campos de "archivedAt" porque hay un endpoint específicamente para ello.
     *
     * @param projectId Identificador/Primary key UUID del proyecto que queremos actualizar.
     * @param update Proyecto con los nuevos datos.
     * @return El proyecto creado en caso exitoso. En caso de fallo, no devuelve nada y lanza una excepción.
     * @throws IllegalArgumentException Si el proyecto no existe.
     */
    @Transactional
    public Project update(UUID projectId, Project update) {
        Project existing = Project.findById(projectId);
        if (existing == null) {
            throw new IllegalArgumentException("El proyecto no existe");
        }

        UUID orgId = userContext.getOrganizationId();
        if (orgId == null || !orgId.equals(existing.organizationId)) {
            throw new IllegalArgumentException("El usuario no tiene permisos para actualizar este proyecto");
        }

        if (update.name != null && !update.name.isBlank()) {
            existing.name = update.name;
        }

        if (update.description != null && !update.description.isBlank()) {
            existing.description = update.description;
        }

        if (update.startDate != null) {
            existing.startDate = update.startDate;
        }

        if (update.endDate != null) {
            existing.endDate = update.endDate;
        }

        existing.updatedAt = OffsetDateTime.now();

        return existing;
    }

    /** Archivar un proyecto existente.
     * <p>
     * // TODO: En un futuro, esta función descargará el proyecto, lo comprimirá y lo subirá a un S3 de AWS.
     * // Por ahora solo cambiamos el estado.
     *
     * @param projectId Identificador/Primary key UUID del proyecto que queremos archivar.
     * @return boolean true si se ha archivado correctamente, false en caso contrario.
     * @throws IllegalArgumentException Si el proyecto no existe.
     */
    @Transactional
    public boolean archive(UUID projectId) {
        Project existing = Project.findById(projectId);
        if (existing == null) {
            throw new IllegalArgumentException("El proyecto no existe");
        }

        UUID orgId = userContext.getOrganizationId();
        if (orgId == null || !orgId.equals(existing.organizationId)) {
            throw new IllegalArgumentException("El usuario no tiene permisos para archivar este proyecto");
        }

        // Si el campo archivedAt ya tiene un valor, significa que ya está archivado. No necesitamos volver a archivarlo.
        if (existing.archivedAt != null) return false;

        existing.archivedAt = OffsetDateTime.now();

        return true;
    }

    /**
     * Desarchivar un proyecto existente.
     * <p>
     * Esta función limpia el campo archivedAt y asegura que el proyecto esté activo.
     *
     * @param projectId Identificador/Primary key UUID del proyecto que queremos desarchivar.
     * @return boolean true si se ha desarchivado correctamente, false en caso contrario.
     */
    @Transactional
    public boolean unarchive(UUID projectId) {
        Project existing = Project.findById(projectId);
        if (existing == null) {
            throw new IllegalArgumentException("El proyecto no existe");
        }

        UUID orgId = userContext.getOrganizationId();
        if (orgId == null || !orgId.equals(existing.organizationId)) {
            throw new IllegalArgumentException("El usuario no tiene permisos para desarchivar este proyecto");
        }

        if (existing.archivedAt == null) return false;

        existing.archivedAt = null;
        existing.projectActive = true; // Lo ponemos en true por si acaso estaba en false
        return true;
    }

    /**
     * Soft delete un proyecto existente.
     * @param projectId Identificador/Primary key UUID del proyecto que queremos eliminar.
     * @return boolean true si se ha eliminado correctamente, false en caso contrario.
     * @throws IllegalArgumentException Si el proyecto no existe.
     */
    @Transactional
    public boolean delete(UUID projectId) {
        Project existing = Project.findById(projectId);
        if (existing == null) {
            throw new IllegalArgumentException("El proyecto no existe");
        }

        UUID orgId = userContext.getOrganizationId();
        if (orgId == null || !orgId.equals(existing.organizationId)) {
            throw new IllegalArgumentException("El usuario no tiene permisos para eliminar este proyecto");
        }

        if (!existing.projectActive) return false;

        existing.projectActive = false;
        return true;
    }

    /**
     * Añade un usuario al equipo del proyecto.
     * <p>
     * Regla de negocio: Un usuario solo puede pertenecer a un proyecto a la vez.
     *
     * @param projectId El ID del proyecto.
     * @param userId El ID del usuario.
     * @return true si se añadió correctamente.
     */
    @Transactional
    public boolean addMember(UUID projectId, UUID userId) {
        // Validar que el proyecto exista y pertenezca a la misma organización que el usuario que lo añade
        Project project = get(projectId);
        if (project == null) {
            throw new IllegalArgumentException("El proyecto no existe o no tienes acceso");
        }

        // 1. Validar que el usuario no esté YA en otro proyecto
        if (ProjectTeam.count("userId", userId) > 0) {
            throw new IllegalStateException("El usuario ya pertenece a un proyecto. Debe salir de su proyecto actual primero.");
        }

        // 2. Añadir al equipo
        ProjectTeam member = new ProjectTeam();
        member.projectId = projectId;
        member.userId = userId;
        member.joinedAt = OffsetDateTime.now();
        member.persist();

        return true;
    }

    /**
     * Quita un usuario del equipo del proyecto.
     *
     * @param projectId El ID del proyecto.
     * @param userId El ID del usuario.
     * @return true si se eliminó correctamente.
     */
    @Transactional
    public boolean removeMember(UUID projectId, UUID userId) {
        return ProjectTeam.delete("projectId = ?1 AND userId = ?2", projectId, userId) > 0;
    }

    /**
     * Obtiene la lista de IDs de usuarios en un proyecto.
     *
     * @param projectId El ID del proyecto.
     * @return Lista de miembros del equipo.
     */
    public List<ProjectTeam> getMembers(UUID projectId) {
        return ProjectTeam.list("projectId", projectId);
    }
}
