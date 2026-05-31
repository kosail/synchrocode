package com.frieren.client;

import com.frieren.dto.AddCollaboratorRequest;
import com.frieren.dto.CreateRepoRequest;
import com.frieren.dto.CreateRepoResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import java.util.List;

@Path("/")
@RegisterRestClient(configKey = "github-api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "Accept", value = "application/vnd.github+json")
@ClientHeaderParam(name = "User-Agent", value = "SynchroCode-Backend")
public interface GitHubClient {

    @POST
    @Path("/orgs/{org}/repos")
    CreateRepoResponse createRepo(@PathParam("org") String org,
                                   @HeaderParam("Authorization") String authHeader,
                                   CreateRepoRequest request);

    @PUT
    @Path("/repos/{org}/{repo}/collaborators/{username}")
    void addCollaborator(@PathParam("org") String org,
                         @PathParam("repo") String repo,
                         @PathParam("username") String username,
                         @HeaderParam("Authorization") String authHeader,
                         AddCollaboratorRequest request);

    @GET
    @Path("/repos/{org}/{repo}/commits")
    List<JsonNode> listCommits(@PathParam("org") String org,
                               @PathParam("repo") String repo,
                               @HeaderParam("Authorization") String authHeader,
                               @QueryParam("per_page") int perPage);

    @GET
    @Path("/repos/{org}/{repo}/pulls")
    List<JsonNode> listPullRequests(@PathParam("org") String org,
                                    @PathParam("repo") String repo,
                                    @HeaderParam("Authorization") String authHeader,
                                    @QueryParam("state") String state,
                                    @QueryParam("per_page") int perPage);

    @GET
    @Path("/repos/{org}/{repo}/collaborators")
    List<JsonNode> listCollaborators(@PathParam("org") String org,
                                     @PathParam("repo") String repo,
                                     @HeaderParam("Authorization") String authHeader,
                                     @QueryParam("affiliation") String affiliation,
                                     @QueryParam("per_page") int perPage);
}
