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

    public record GitHubCollaboratorRequest(String githubUsername) {}
    public record MemberGitHubRequest(String githubUsername) {}

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

    @POST
    @Path("/{id}/github/repository")
    public Project createGitHubRepository(@PathParam("id") UUID projectId) {
        return service.createGitHubRepository(projectId);
    }

    @POST
    @Path("/{id}/github/collaborators")
    public void addGitHubCollaborator(
            @PathParam("id") UUID projectId,
            GitHubCollaboratorRequest request
    ) {
        service.addGitHubCollaborator(projectId, request.githubUsername());
    }

    @GET
    @Path("/{id}/github/stats")
    public com.frieren.dto.GitHubStatsResponse getGitHubStats(@PathParam("id") UUID projectId) {
        return service.getGitHubStats(projectId);
    }

    @GET
    @Path("/{id}/github/collaborators")
    public List<com.frieren.dto.GitHubCollaboratorResponse> getGitHubCollaborators(@PathParam("id") UUID projectId) {
        return service.getGitHubCollaborators(projectId);
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
    public boolean addMember(
            @PathParam("id") UUID projectId,
            @PathParam("userId") UUID userId,
            @QueryParam("githubUsername") String githubUsername
    ) {
        return service.addMember(projectId, userId, githubUsername);
    }

    @PUT
    @Path("/{id}/members/{userId}/github")
    public com.frieren.dto.ProjectMemberDTO updateMemberGitHubUsername(
            @PathParam("id") UUID projectId,
            @PathParam("userId") UUID userId,
            MemberGitHubRequest request
    ) {
        return service.updateMemberGitHubUsername(projectId, userId, request.githubUsername());
    }

    @DELETE
    @Path("/{id}/members/{userId}")
    public boolean removeMember(@PathParam("id") UUID projectId, @PathParam("userId") UUID userId) {
        return service.removeMember(projectId, userId);
    }

    @GET
    @Path("/{id}/members")
    public List<com.frieren.dto.ProjectMemberDTO> getMembers(@PathParam("id") UUID projectId) {
        return service.getMembers(projectId);
    }
}
