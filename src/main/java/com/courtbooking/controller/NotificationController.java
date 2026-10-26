package com.courtbooking.controller;

import com.courtbooking.dto.NotificationDtos.NotificationResponse;
import com.courtbooking.dto.NotificationDtos.NotificationsResponse;
import com.courtbooking.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // The 20 newest, and how many are unread
    @GetMapping("/me")
    public NotificationsResponse myNotifications(@AuthenticationPrincipal(expression = "id") Long userId) {
        return new NotificationsResponse(
                notificationService.unreadCount(userId),
                notificationService.getRecent(userId).stream().map(NotificationResponse::from).toList());
    }

    @PostMapping("/me/read")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal(expression = "id") Long userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }
}
