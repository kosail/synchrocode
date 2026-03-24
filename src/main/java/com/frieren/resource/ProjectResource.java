package com.frieren.resource;

import com.frieren.entity.Project;
import com.frieren.security.UserContext;
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
    @Inject UserContext userContext;

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
    public Project get() {
        return service.get(userContext.getUserId());
    }

    @POST
    public Project create(@RequestBody Project project) {
        return service.create(project);
    }

    @PUT
    public Project update(@RequestBody Project updated, @QueryParam("projectId") UUID projectId) {
        return service.update(projectId, updated);
    }

    @PUT
    @Path("/archive")
    public boolean archive(@QueryParam("projectId") UUID projectId) {
        return service.archive(projectId);
    }

    @DELETE
    public boolean delete(@QueryParam("projectId") UUID projectId) {
        return service.delete(projectId);
    }
}
