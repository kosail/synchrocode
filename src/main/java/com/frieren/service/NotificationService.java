package com.frieren.service;

import com.frieren.dto.NotificationResponse;
import com.frieren.entity.Notification;
import com.frieren.entity.NotificationType;
import com.frieren.entity.ProjectTeam;
import com.frieren.security.UserContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class NotificationService {
    public static final short TASK_ASSIGNED = 1;
    public static final short ROLE_CHANGED = 2;
    public static final short CHAT_MESSAGE = 3;
    public static final short TASK_STATUS_UPDATED = 4;
    public static final short PR_MERGED = 5;
    public static final short SESSION_CONTROL_REQUEST = 6;

    @Inject UserContext userContext;

    public List<NotificationResponse> listMine() {
        UUID userId = userContext.getUserId();
        return Notification.find("userId = ?1 order by createdAt desc", userId)
                .page(0, 50)
                .<Notification>list()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public long unreadCountMine() {
        return Notification.count("userId = ?1 and isRead = false", userContext.getUserId());
    }

    @Transactional
    public NotificationResponse notifyUser(UUID userId, short typeId, String title, String body, String relatedEntity, UUID relatedId) {
        if (userId == null) return null;

        NotificationType type = NotificationType.findById(typeId);
        if (type == null) {
            throw new IllegalArgumentException("Tipo de notificación no existe: " + typeId);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setRelatedEntity(relatedEntity);
        notification.setRelatedId(relatedId);
        notification.setIsRead(false);
        notification.setCreatedAt(now);
        notification.persist();

        return toResponse(notification);
    }

    @Transactional
    public void notifyProjectTeam(UUID projectId, short typeId, String title, String body, String relatedEntity, UUID relatedId, UUID excludeUserId) {
        if (projectId == null) return;

        List<ProjectTeam> members = ProjectTeam.list("projectId", projectId);
        for (ProjectTeam member : members) {
            if (excludeUserId != null && excludeUserId.equals(member.userId)) continue;
            notifyUser(member.userId, typeId, title, body, relatedEntity, relatedId);
        }
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        Notification notification = ownedNotification(notificationId);
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(OffsetDateTime.now());
        }
        return toResponse(notification);
    }

    @Transactional
    public void markAllRead() {
        List<Notification> notifications = Notification.list("userId = ?1 and isRead = false", userContext.getUserId());
        OffsetDateTime now = OffsetDateTime.now();
        for (Notification notification : notifications) {
            notification.setIsRead(true);
            notification.setReadAt(now);
        }
    }

    @Transactional
    public boolean deleteMine(UUID notificationId) {
        Notification notification = ownedNotification(notificationId);
        notification.delete();
        return true;
    }

    private Notification ownedNotification(UUID notificationId) {
        if (notificationId == null) {
            throw new IllegalArgumentException("El ID de la notificación es obligatorio");
        }

        Notification notification = Notification.findById(notificationId);
        if (notification == null || !userContext.getUserId().equals(notification.getUserId())) {
            throw new IllegalArgumentException("La notificación no existe");
        }
        return notification;
    }

    private NotificationResponse toResponse(Notification notification) {
        NotificationType type = notification.getType();
        return new NotificationResponse(
                notification.getId(),
                type != null ? type.getId() : null,
                type != null ? type.getName() : null,
                notification.getTitle(),
                notification.getBody(),
                notification.getRelatedEntity(),
                notification.getRelatedId(),
                notification.getIsRead(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
