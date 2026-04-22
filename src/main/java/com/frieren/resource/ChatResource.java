package com.frieren.resource;

import com.frieren.dto.CreateChatChannelRequest;
import com.frieren.dto.CreateChatChannelResponse;
import com.frieren.service.ChatService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

@Path("/chat/channels")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ChatResource {
    @Inject ChatService chatService;

    @POST
    public CreateChatChannelResponse createChannel(CreateChatChannelRequest request) {
        return chatService.createChannel(request);
    }

    @GET
    public List<CreateChatChannelResponse> listChannels(@QueryParam("projectId") UUID projectId) {
        return chatService.listChannels(projectId);
    }
}