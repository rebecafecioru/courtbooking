package com.courtbooking.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A message for one user, shown in the app, e.g. "you got a slot from the
 * waitlist". Written in the same transaction as whatever it's about, so it
 * exists exactly when that thing happened.
 */
@Entity
@Table(name = "notification", indexes = @Index(name = "idx_notification_user", columnList = "user_id, createdAt"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 500)
    private String message;

    private LocalDateTime createdAt;

    // null until the user has seen it
    private LocalDateTime readAt;

    protected Notification() {
    }

    public Notification(AppUser user, String message) {
        this.user = user;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }
}
