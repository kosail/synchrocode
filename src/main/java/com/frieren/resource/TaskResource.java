package com.frieren.resource;

import com.frieren.entity.Task;
import com.frieren.entity.TaskEvidence;
import com.frieren.service.TaskService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/tasks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TaskResource {
    @Inject TaskService service;

    @GET
    @Path("/project/{projectId}")
    public List<Task> getTasksByProject(@PathParam("projectId") UUID projectId) {
        return service.getTasksByProject(projectId);
    }

    @GET
    @Path("/{id}")
    public Task get(@PathParam("id") UUID id) {
        return service.getTask(id);
    }

    @POST
    public Task create(Task task) {
        return service.create(task);
    }

    @PUT
    @Path("/{id}")
    public Task update(@PathParam("id") UUID id, Task task) {
        return service.update(id, task);
    }

    @PATCH
    @Path("/{id}/status/{statusId}")
    public Task updateStatus(@PathParam("id") UUID id, @PathParam("statusId") Short statusId) {
        return service.updateStatus(id, statusId);
    }

    @PATCH
    @Path("/{id}/assign/{userId}")
    public Task assignTask(@PathParam("id") UUID id, @PathParam("userId") UUID userId) {
        return service.assignTask(id, userId);
    }

    @DELETE
    @Path("/{id}")
    public boolean delete(@PathParam("id") UUID id) {
        return service.delete(id);
    }

    @POST
    @Path("/{id}/evidence")
    public TaskEvidence addEvidence(@PathParam("id") UUID id, TaskEvidence evidence) {
        // Solo tomamos los datos necesarios, el resto se genera en el Service
        return service.addEvidence(id, evidence.fileName, evidence.fileUrl, evidence.fileSizeBytes);
    }

    @GET
    @Path("/{id}/evidence")
    public List<TaskEvidence> getEvidence(@PathParam("id") UUID id) {
        return service.getEvidence(id);
    }

    @DELETE
    @Path("/evidence/{evidenceId}")
    public boolean removeEvidence(@PathParam("evidenceId") UUID evidenceId) {
        return service.removeEvidence(evidenceId);
    }

    @GET
    @Path("/evidence/{evidenceId}/download")
    public java.util.Map<String, String> getDownloadUrl(@PathParam("evidenceId") UUID evidenceId) {
        return java.util.Map.of("url", service.getEvidenceDownloadUrl(evidenceId));
    }
}
