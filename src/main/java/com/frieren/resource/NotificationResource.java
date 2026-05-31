package com.frieren.resource;

import com.frieren.dto.NotificationResponse;
import com.frieren.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NotificationResource {
    @Inject NotificationService service;

    @GET
    public List<NotificationResponse> listMine() {
        return service.listMine();
    }

    @GET
    @Path("/unread-count")
    public Map<String, Long> unreadCountMine() {
        return Map.of("count", service.unreadCountMine());
    }

    @PUT
    @Path("/{id}/read")
    public NotificationResponse markRead(@PathParam("id") UUID notificationId) {
        return service.markRead(notificationId);
    }

    @PUT
    @Path("/read-all")
    public void markAllRead() {
        service.markAllRead();
    }

    @DELETE
    @Path("/{id}")
    public boolean deleteMine(@PathParam("id") UUID notificationId) {
        return service.deleteMine(notificationId);
    }
}
