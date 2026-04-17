package com.frieren.resource;

import com.frieren.dto.RoleRequest;
import com.frieren.dto.RoleResponse;
import com.frieren.service.RoleService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/roles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoleResource {

    @Inject
    RoleService roleService;

    @POST
    public Response create(RoleRequest request) {
        RoleResponse response = roleService.createRole(request);
        return Response.status(Response.Status.CREATED).entity(response).build();
    }

    @GET
    public List<RoleResponse> getAll() {
        return roleService.getAllRoles();
    }

    @GET
    @Path("/{id}")
    public RoleResponse getById(@PathParam("id") Short id) {
        return roleService.getRoleById(id);
    }

    @PUT
    @Path("/{id}")
    public RoleResponse update(@PathParam("id") Short id, RoleRequest request) {
        return roleService.updateRole(id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Short id) {
        roleService.deleteRole(id);
        return Response.noContent().build();
    }
}
