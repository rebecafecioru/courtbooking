package com.courtbooking.dto;

import com.courtbooking.model.Notification;

import java.time.LocalDateTime;
import java.util.List;

public class NotificationDtos {

    public record NotificationResponse(Long id, String message, LocalDateTime createdAt, boolean read) {
        public static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getMessage(), n.getCreatedAt(), n.getReadAt() != null);
        }
    }

    public record NotificationsResponse(long unreadCount, List<NotificationResponse> notifications) {
    }
}
