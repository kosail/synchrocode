package com.frieren.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frieren.dto.CreateChatChannelRequest;
import com.frieren.dto.CreateChatChannelResponse;
import com.frieren.entity.ChatChannel;
import com.frieren.entity.ChatMessage;
import com.frieren.entity.Project;
import com.frieren.security.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ChatService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject UserContext userContext;

    @Transactional
    public CreateChatChannelResponse createChannel(CreateChatChannelRequest request) {
        if (request == null || request.projectId() == null) {
            throw new IllegalArgumentException("Project id is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Channel name is required");
        }

        String normalizedName = request.name().trim();

        Project project = Project.findById(request.projectId());
        if (project == null || !project.projectActive) {
            throw new IllegalArgumentException("Project does not exist or is inactive");
        }

        if (ChatChannel.count("project.id = ?1 and deletedAt is null and lower(name) = lower(?2)", request.projectId(), normalizedName) > 0) {
            throw new IllegalArgumentException("Channel name already exists in this project");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ChatChannel channel = new ChatChannel();
        channel.setId(UUID.randomUUID());
        channel.setProject(project);
        channel.setName(normalizedName);
        channel.setCreatedAt(now);
        channel.setUpdatedAt(now);
        channel.persist();

        return new CreateChatChannelResponse(channel.getId(), project.id, channel.getName(), "/ws/chat/" + channel.getId());
    }

    public List<CreateChatChannelResponse> listChannels(UUID projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project id is required");
        }

        Project project = Project.findById(projectId);
        if (project == null || !project.projectActive) {
            throw new IllegalArgumentException("Project does not exist or is inactive");
        }

        return ChatChannel.find("project.id = ?1 and deletedAt is null order by updatedAt desc", projectId)
                .<ChatChannel>list()
                .stream()
                .map(channel -> new CreateChatChannelResponse(channel.getId(), projectId, channel.getName(), "/ws/chat/" + channel.getId()))
                .toList();
    }

    public ChannelPage joinChannel(UUID channelId, int page, int size) {
        ChatChannel channel = getActiveChannelOrThrow(channelId);
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(size, 1);
        return buildPage(channel, normalizedPage, normalizedSize);
    }

    @Transactional
    public ChatMessagePayload sendMessage(UUID channelId, UUID userId, String body, List<String> imageUrls) {
        ChatChannel channel = getActiveChannelOrThrow(channelId);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Message text is required");
        }

        List<String> normalizedImageUrls = normalizeImageUrls(imageUrls);
        OffsetDateTime now = OffsetDateTime.now();

        ChatMessage message = new ChatMessage();
        // REMOVED: message.setId(UUID.randomUUID()); <-- Hibernate handles this
        message.setChannel(channel);
        message.setUserId(userId != null ? userId : userContext.getUserId());
        message.setBody(body);
        message.setImageUrls(writeImageUrls(normalizedImageUrls));
        message.setSentAt(now);
        message.persist();

        channel.setUpdatedAt(now);

        return toPayload(message);
    }

    @Transactional
    public ChatMessagePayload editMessage(UUID channelId, UUID messageId, UUID userId, String body, List<String> imageUrls) {
        if (messageId == null) {
            throw new IllegalArgumentException("Message id is required");
        }

        ChatChannel channel = getActiveChannelOrThrow(channelId);
        ChatMessage message = ChatMessage.findById(messageId);
        if (message == null || !message.getChannel().getId().equals(channel.getId())) {
            throw new IllegalArgumentException("Message not found in this channel");
        }

        UUID editorId = userId != null ? userId : userContext.getUserId();
        if (!editorId.equals(message.getUserId())) {
            throw new SecurityException("You can only edit your own messages");
        }

        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Message text is required");
        }

        message.setBody(body);
        message.setImageUrls(writeImageUrls(normalizeImageUrls(imageUrls)));
        message.setEditedAt(OffsetDateTime.now());
        channel.setUpdatedAt(OffsetDateTime.now());
        return toPayload(message);
    }

    protected ChatChannel getActiveChannelOrThrow(UUID channelId) {
        if (channelId == null) {
            throw new IllegalArgumentException("Channel id is required");
        }
        ChatChannel channel = ChatChannel.findById(channelId);
        if (channel == null || channel.getDeletedAt() != null) {
            throw new IllegalArgumentException("Channel not found");
        }
        return channel;
    }

    private ChannelPage buildPage(ChatChannel channel, int page, int size) {
        List<ChatMessage> messages = ChatMessage.find("channel = ?1 order by sentAt desc", channel)
                .page(page, size)
                .list();

        long total = ChatMessage.count("channel", channel);
        List<ChatMessagePayload> items = messages.stream().map(this::toPayload).toList();

        return new ChannelPage(channel.getId(), page, size, total, items);
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            if (imageUrl != null && !imageUrl.isBlank()) {
                normalized.add(imageUrl);
            }
        }
        return normalized;
    }

    private String writeImageUrls(List<String> imageUrls) {
        try {
            return MAPPER.writeValueAsString(imageUrls);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid image urls payload", e);
        }
    }

    private List<String> readImageUrls(String imageUrlsJson) {
        if (imageUrlsJson == null || imageUrlsJson.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(imageUrlsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private ChatMessagePayload toPayload(ChatMessage message) {
        return new ChatMessagePayload(
                message.getId(),
                message.getChannel().getId(),
                message.getUserId(),
                message.getBody(),
                readImageUrls(message.getImageUrls()),
                message.getSentAt(),
                message.getEditedAt()
        );
    }

    public record ChannelPage(
            UUID channelId,
            int page,
            int size,
            long total,
            List<ChatMessagePayload> messages
    ) {
    }

    public record ChatMessagePayload(
            UUID id,
            UUID channelId,
            UUID userId,
            String text,
            List<String> imageUrls,
            OffsetDateTime sentAt,
            OffsetDateTime editedAt
    ) {
    }
}