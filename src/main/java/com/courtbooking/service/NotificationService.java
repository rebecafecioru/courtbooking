package com.courtbooking.service;

import com.courtbooking.model.AppUser;
import com.courtbooking.model.Notification;
import com.courtbooking.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * In-app notifications. notify() joins the caller's transaction, so a
 * notification is only saved if what it describes is too (e.g. a waitlist
 * place turning into a booking).
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notify(AppUser user, String message) {
        notificationRepository.save(new Notification(user, message));
    }

    @Transactional(readOnly = true)
    public List<Notification> getRecent(Long userId) {
        return notificationRepository.findTop20ByUserIdOrderByCreatedAtDescIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, LocalDateTime.now());
    }
}
