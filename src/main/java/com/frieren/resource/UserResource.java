package com.frieren.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.frieren.service.UserService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {
    @Inject
    UserService service;

    public record InviteRequest(String email, String name, String role, String redirectTo) {}
    public record UpdateRoleRequest(String role) {}
    public record UpdateNameRequest(String name) {}

    @GET
    public ArrayNode getAll() {
        return service.listByOrganization();
    }

    @GET
    @Path("/{id}")
    public ObjectNode get(@PathParam("id") UUID id) {
        return service.get(id);
    }

    @POST
    @Path("/invite")
    public JsonNode invite(InviteRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        if (request.role() == null || request.role().isBlank()) {
            throw new IllegalArgumentException("El rol es obligatorio");
        }

        var redirectTo = request.redirectTo() != null ? request.redirectTo() : "http://localhost:4321/activar-cuenta";
        return service.invite(request.email(), request.name(), request.role(), redirectTo);
    }

    @PUT
    @Path("/{id}/name")
    public ObjectNode updateName(@PathParam("id") UUID id, UpdateNameRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }

        return service.updateName(id, request.name());
    }

    @PUT
    @Path("/{id}/role")
    public ObjectNode updateRole(@PathParam("id") UUID id, UpdateRoleRequest request) {
        if (request.role() == null || request.role().isBlank()) {
            throw new IllegalArgumentException("El rol es obligatorio");
        }

        return service.updateRole(id, request.role());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
