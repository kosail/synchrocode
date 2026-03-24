package com.frieren.service;

import com.frieren.entity.Project;
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
     * Para obtener todos los proyectos
     * @return Lista de todos los proyectos incluyendo proyectos activos y no activos, y proyectos archivados.
     */
    public List<Project> getAll() {
        return Project.listAll();
    }

    /**
     * Para obtener todos los proyectos activos y no archivados.
     * @return Lista de todos los proyectos activos y no archivados.
     */
    public List<Project> getActive() {
        return Project.list("projectActive = true AND archivedAt IS NULL");
    }

    /**
     * Para obtener un proyecto buscando por ID
     * @param id Identificador/Primary key UUID del proyecto
     * @return El proyecto o null si no existe
     */
    public Project get(UUID id) {
        return Project.findById(id);
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
        project.projectActive = true;
        project.createdBy = userContext.getUserId();
        project.createdAt = now;
        project.updatedAt = now;

        project.persist();
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
     * TODO: Necesitamos que esta función descargue el proyecto, lo comprima y lo suba a un S3 de AWS, o que lo guarde en local en lightsail al menos
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

        // Si el campo archivedAt ya tiene un valor, significa que ya está archivado. No necesitamos volver a archivarlo.
        if (existing.archivedAt != null) return false;

        existing.archivedAt = OffsetDateTime.now();

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

        if (!existing.projectActive) return false;

        existing.projectActive = false;
        return true;
    }
}
