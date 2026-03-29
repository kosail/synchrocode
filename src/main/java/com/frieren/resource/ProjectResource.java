package com.frieren.resource;

import com.frieren.entity.Project;
import com.frieren.service.ProjectService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;
import java.util.UUID;

@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ProjectResource {
    @Inject ProjectService service;

    @GET
    @Path("/all")
    public List<Project> getAll() {
        return service.getAll();
    }

    @GET
    @Path("/active")
    public List<Project> getActive() {
        return service.getActive();
    }

    @GET
    @Path("/archive/all")
    public List<Project> getArchived() {
        return service.getArchived();
    }

    @GET
    @Path("/{id}")
    public Project get(@PathParam("id") UUID id) {
        return service.get(id);
    }

    @POST
    public Project create(@RequestBody Project project) {
        return service.create(project);
    }

    @PUT
    @Path("/{id}")
    public Project update(@RequestBody Project updated, @PathParam("id") UUID projectId) {
        return service.update(projectId, updated);
    }

    @PUT
    @Path("/archive/{id}")
    public boolean archive(@PathParam("id") UUID projectId) {
        return service.archive(projectId);
    }

    @DELETE
    @Path("/{id}")
    public boolean delete(@PathParam("id") UUID id) {
        return service.delete(id);
    }
}
