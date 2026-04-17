package com.frieren.resource;

import com.frieren.entity.Project;
import com.frieren.entity.ProjectTeam;
import com.frieren.service.ProjectService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

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
    public Project create(Project project) {
        return service.create(project);
    }

    @PUT
    @Path("/{id}")
    public Project update(@PathParam("id") UUID projectId, Project updated) {
        return service.update(projectId, updated);
    }

    @PUT
    @Path("/archive/{id}")
    public boolean archive(@PathParam("id") UUID projectId) {
        return service.archive(projectId);
    }

    @PUT
    @Path("/unarchive/{id}")
    public boolean unarchive(@PathParam("id") UUID projectId) {
        return service.unarchive(projectId);
    }

    @DELETE
    @Path("/{id}")
    public boolean delete(@PathParam("id") UUID id) {
        return service.delete(id);
    }

    @POST
    @Path("/{id}/members/{userId}")
    public boolean addMember(@PathParam("id") UUID projectId, @PathParam("userId") UUID userId) {
        return service.addMember(projectId, userId);
    }

    @DELETE
    @Path("/{id}/members/{userId}")
    public boolean removeMember(@PathParam("id") UUID projectId, @PathParam("userId") UUID userId) {
        return service.removeMember(projectId, userId);
    }

    @GET
    @Path("/{id}/members")
    public List<ProjectTeam> getMembers(@PathParam("id") UUID projectId) {
        return service.getMembers(projectId);
    }
}
