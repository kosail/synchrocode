package com.frieren.resource;

import com.frieren.dto.CollaborativeSessionSummaryResponse;
import com.frieren.dto.CreateCollaborativeSessionRequest;
import com.frieren.dto.JoinCollaborativeSessionRequest;
import com.frieren.dto.JoinCollaborativeSessionResponse;
import com.frieren.dto.StartCollaborativeSessionResponse;
import com.frieren.service.CollaborativeSessionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

@Path("/collaborative-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CollaborativeSessionResource {
    @Inject CollaborativeSessionService service;

    @POST
    public StartCollaborativeSessionResponse start(CreateCollaborativeSessionRequest request) {
        return service.startSession(request);
    }

    @GET
    @Path("/active")
    public List<CollaborativeSessionSummaryResponse> active() {
        return service.listActiveSessions();
    }

    @POST
    @Path("/{sessionId}/join")
    public JoinCollaborativeSessionResponse join(@PathParam("sessionId") UUID sessionId, JoinCollaborativeSessionRequest request) {
        return service.joinSession(sessionId, request != null ? request.passcode() : null);
    }

    @PUT
    @Path("/{sessionId}/leave")
    public void leave(@PathParam("sessionId") UUID sessionId) {
        service.leaveSession(sessionId);
    }
}
