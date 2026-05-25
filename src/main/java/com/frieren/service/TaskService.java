package com.frieren.service;

import com.frieren.entity.Task;
import com.frieren.entity.Project;
import com.frieren.entity.ProjectTeam;
import com.frieren.entity.TaskEvidence;
import com.frieren.entity.Role;
import com.frieren.repository.RoleRepository;
import com.frieren.security.UserContext;
import com.frieren.security.models.Roles;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@ApplicationScoped
public class TaskService {
    @Inject UserContext userContext;
    @Inject ProjectService projectService;
    @Inject RoleRepository roleRepository;
    @Inject SupabaseAdminService supabaseAdminService;
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(TaskService.class.getName());

    /**
     * Verifica si el usuario tiene un permiso específico en un módulo.
     */
    private boolean hasPermission(String module, String action) {
        String roleName = userContext.role();
        if (Roles.ADMIN.equalsIgnoreCase(roleName)) return true;

        UUID orgId = userContext.getOrganizationId();
        if (orgId == null) return false;

        // Búsqueda insensible a mayúsculas para evitar fallos de sincronización
        return roleRepository.find("upper(name) = ?1 and (organization.id is null or organization.id = ?2)", 
                roleName.toUpperCase().trim(), orgId).firstResultOptional()
                .map(Role::getPermissions)
                .map(perms -> {
                    List<String> actions = perms.get(module);
                    return actions != null && actions.contains(action);
                })
                .orElse(false);
    }

    /**
     * Valida acceso básico al proyecto (read).
     */
    private void checkProjectAccess(UUID projectId) {
        if (!hasPermission("projects_module", "read") && !hasPermission("tasks", "read")) {
            LOG.warning("Acceso denegado: Usuario no tiene permisos de lectura en módulos de tareas/proyectos");
            throw new SecurityException("No tienes permiso de lectura");
        }

        if (!Roles.ADMIN.equalsIgnoreCase(userContext.role()) && !isMemberOfProject(projectId, userContext.getUserId())) {
            LOG.warning("Acceso denegado: Usuario no es miembro del equipo del proyecto " + projectId);
            throw new SecurityException("No eres miembro de este proyecto");
        }
    }

    public List<Task> getAll() {
        String roleName = userContext.role();
        UUID userId = userContext.getUserId();
        List<Task> tasks;

        if (Roles.ADMIN.equalsIgnoreCase(roleName)) {
            tasks = Task.listAll();
        } else {
            // Obtener IDs de proyectos donde el usuario es miembro
            List<ProjectTeam> teams = ProjectTeam.list("userId", userId);
            if (teams.isEmpty()) return List.of();
            List<UUID> projectIds = teams.stream().map(pt -> pt.projectId).toList();
            tasks = Task.find("projectId in ?1", projectIds).list();
        }

        tasks.forEach(this::populateEvidence);
        return tasks;
    }

    public List<Task> getTasksByProject(UUID projectId) {
        checkProjectAccess(projectId);
        List<Task> tasks = Task.list("projectId", projectId);
        // Poblar evidencias para cada tarea
        tasks.forEach(this::populateEvidence);
        return tasks;
    }

    public Task getTask(UUID taskId) {
        if (taskId == null) return null;
        Task task = Task.findById(taskId);
        if (task == null) return null;
        
        checkProjectAccess(task.projectId);
        populateEvidence(task);
        return task;
    }

    private void populateEvidence(Task task) {
        if (task == null || task.id == null) return;
        task.evidence = TaskEvidence.list("taskId", task.id);
    }

    @Transactional
    public Task create(Task task) {
        if (!hasPermission("tasks", "create")) {
            throw new SecurityException("No tienes permiso para crear tareas");
        }

        checkProjectAccess(task.projectId);

        Project project = projectService.get(task.projectId);
        if (project == null || !project.projectActive) {
            throw new IllegalArgumentException("El proyecto no existe o no está activo");
        }

        if (task.title == null || task.title.isBlank()) {
            throw new IllegalArgumentException("El título de la tarea es obligatorio");
        }

        var now = OffsetDateTime.now();
        task.id = UUID.randomUUID();

        if (task.jiraTaskId == null || task.jiraTaskId.isBlank()) {
            task.jiraTaskId = "TASK-" + task.id.toString().substring(0, 8).toUpperCase();
        }

        task.statusId = (task.statusId != null) ? task.statusId : 1;
        task.priorityId = (task.priorityId != null) ? task.priorityId : 2;
        task.createdBy = userContext.getUserId();
        task.createdAt = now;
        task.updatedAt = now;

        task.persist();
        return task;
    }

    @Transactional
    public Task update(UUID taskId, Task update) {
        if (taskId == null) throw new IllegalArgumentException("El ID de la tarea es obligatorio");
        Task existing = Task.findById(taskId);
        if (existing == null) throw new IllegalArgumentException("Esta tarea ya no existe");

        checkProjectAccess(existing.projectId);

        boolean canUpdateAny = hasPermission("tasks", "update");
        boolean canUpdateOwn = hasPermission("tasks", "update_own");

        if (!canUpdateAny) {
            if (canUpdateOwn) {
                UUID userId = userContext.getUserId();
                if (!userId.equals(existing.assignedTo) && !userId.equals(existing.createdBy)) {
                    throw new SecurityException("Solo tienes permiso para editar tus propias tareas.");
                }
            } else {
                throw new SecurityException("No tienes permiso para editar tareas.");
            }
        }

        if (update.title != null && !update.title.isBlank()) existing.title = update.title;
        if (update.description != null) existing.description = update.description;
        if (update.priorityId != null) existing.priorityId = update.priorityId;
        if (update.dueDate != null) existing.dueDate = update.dueDate;
        if (update.statusId != null) existing.statusId = update.statusId;
        
        if (update.assignedTo != null) {
            if (!isMemberOfProject(existing.projectId, update.assignedTo)) {
                throw new IllegalArgumentException("El usuario asignado no es miembro del proyecto");
            }
            existing.assignedTo = update.assignedTo;
        }

        existing.updatedAt = OffsetDateTime.now();
        populateEvidence(existing);
        return existing;
    }

    @Transactional
    public boolean delete(UUID taskId) {
        if (taskId == null) throw new IllegalArgumentException("El ID de la tarea es obligatorio");
        Task existing = Task.findById(taskId);
        if (existing == null) throw new IllegalArgumentException("Esta tarea ya no existe");

        if (!hasPermission("tasks", "delete")) {
            throw new SecurityException("No tienes permiso para eliminar tareas");
        }

        checkProjectAccess(existing.projectId);

        if (existing.statusId != 1) {
            throw new IllegalStateException("Solo se pueden eliminar tareas pendientes");
        }

        return Task.deleteById(taskId);
    }

    @Transactional
    public Task updateStatus(UUID taskId, Short statusId) {
        if (taskId == null) throw new IllegalArgumentException("El ID de la tarea es obligatorio");
        Task existing = Task.findById(taskId);
        if (existing == null) throw new IllegalArgumentException("Esta tarea ya no existe");

        checkProjectAccess(existing.projectId);

        boolean canUpdateAny = hasPermission("tasks", "update");
        boolean canUpdateOwn = hasPermission("tasks", "update_own");

        if (!canUpdateAny) {
            if (canUpdateOwn) {
                UUID userId = userContext.getUserId();
                if (!userId.equals(existing.assignedTo) && !userId.equals(existing.createdBy)) {
                    LOG.warning("Acceso denegado: Usuario con update_own intentó mover tarea ajena.");
                    throw new SecurityException("Solo puedes cambiar el estado de tus propias tareas.");
                }
            } else {
                LOG.warning("Acceso denegado: Usuario no tiene permiso update ni update_own.");
                throw new SecurityException("No tienes permiso para actualizar tareas.");
            }
        }

        existing.statusId = statusId;
        var now = OffsetDateTime.now();
        existing.updatedAt = now;
        
        if (statusId == 3) {
            existing.completedAt = now;
        } else {
            existing.completedAt = null;
        }

        populateEvidence(existing);
        return existing;
    }

    @Transactional
    public Task assignTask(UUID taskId, UUID assignedTo) {
        if (taskId == null) throw new IllegalArgumentException("El ID de la tarea es obligatorio");
        Task existing = Task.findById(taskId);
        if (existing == null) throw new IllegalArgumentException("Esta tarea ya no existe");

        if (!hasPermission("tasks", "update")) {
            throw new SecurityException("No tienes permiso para asignar tareas");
        }

        checkProjectAccess(existing.projectId);

        if (assignedTo != null && !isMemberOfProject(existing.projectId, assignedTo)) {
            throw new IllegalArgumentException("El usuario no es miembro del proyecto");
        }

        existing.assignedTo = assignedTo;
        existing.updatedAt = OffsetDateTime.now();

        return existing;
    }

    @Transactional
    public TaskEvidence addEvidence(UUID taskId, String fileName, String fileUrl, Long fileSizeBytes) {
        if (taskId == null) throw new IllegalArgumentException("El ID de la tarea es obligatorio");
        Task existing = Task.findById(taskId);
        if (existing == null) throw new IllegalArgumentException("Esta tarea ya no existe");

        checkProjectAccess(existing.projectId);

        // Validación de tamaño (50MB = 52428800 bytes)
        if (fileSizeBytes > 52428800) {
            throw new IllegalArgumentException("El archivo no debe superar los 50 MB");
        }

        TaskEvidence evidence = new TaskEvidence();
        evidence.id = UUID.randomUUID();
        evidence.taskId = taskId;
        evidence.fileName = fileName;
        evidence.fileUrl = fileUrl;
        evidence.fileSizeBytes = fileSizeBytes;
        evidence.uploadedBy = userContext.getUserId();
        evidence.uploadedAt = OffsetDateTime.now();
        
        evidence.persist();
        return evidence;
    }

    public List<TaskEvidence> getEvidence(UUID taskId) {
        if (taskId == null) throw new IllegalArgumentException("El ID de la tarea es obligatorio");
        Task existing = Task.findById(taskId);
        if (existing == null) throw new IllegalArgumentException("Esta tarea ya no existe");
        
        checkProjectAccess(existing.projectId);
        return TaskEvidence.list("taskId", taskId);
    }

    public String getEvidenceDownloadUrl(UUID evidenceId) {
        if (evidenceId == null) throw new IllegalArgumentException("ID de evidencia es obligatorio");
        TaskEvidence evidence = TaskEvidence.findById(evidenceId);
        if (evidence == null) throw new IllegalArgumentException("La evidencia no existe");

        if (evidence.taskId == null) throw new IllegalStateException("Esta evidencia no tiene una tarea asociada");
        Task task = Task.findById(evidence.taskId);
        if (task == null) throw new IllegalArgumentException("La tarea asociada a esta evidencia ya no existe");

        checkProjectAccess(task.projectId);

        // Supabase requiere el path relativo dentro del bucket
        String path = evidence.fileUrl;

        return supabaseAdminService.getSignedUrl("task-evidence", path, 900);
    }

    @Transactional
    public boolean removeEvidence(UUID evidenceId) {
        if (evidenceId == null) return false;
        TaskEvidence evidence = TaskEvidence.findById(evidenceId);
        if (evidence == null) return false;

        if (evidence.taskId == null) return TaskEvidence.deleteById(evidenceId);

        Task task = Task.findById(evidence.taskId);
        if (task == null) return TaskEvidence.deleteById(evidenceId);

        UUID userId = userContext.getUserId();

        // Puede borrar si tiene permiso de delete en tasks o si es el dueño del archivo
        if (!hasPermission("tasks", "delete") && !evidence.uploadedBy.equals(userId)) {
            throw new SecurityException("No tienes permiso para eliminar esta evidencia");
        }

        return TaskEvidence.deleteById(evidenceId);
    }
    private boolean isMemberOfProject(UUID projectId, UUID userId) {
        return ProjectTeam.count("projectId = ?1 AND userId = ?2", projectId, userId) > 0;
    }
}
