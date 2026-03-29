package com.frieren.resource;

import com.frieren.entity.Organization;
import com.frieren.service.OrganizationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;

import java.util.List;
import java.util.UUID;

@Path("/organizations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrganizationResource {
    @Inject
    OrganizationService service;

    @GET
    @Path("/all")
    public List<Organization> getAll() {
        return service.getAll();
    }

    @GET
    @Path("/{id}")
    public Organization get(@PathParam("id") UUID id) {
        return service.get(id);
    }

    @POST
    public Organization create(@RequestBody Organization organization) {
        return service.create(organization);
    }

    @PUT
    @Path("/{id}")
    public Organization update(@PathParam("id") UUID id, @RequestBody Organization organization) {
        return service.update(id, organization);
    }

    @DELETE
    @Path("/{id}")
    public boolean delete(@PathParam("id") UUID id) {
        return service.delete(id);
    }
}
