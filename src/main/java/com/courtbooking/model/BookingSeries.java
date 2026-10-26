package com.courtbooking.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A weekly repeating booking, e.g. a club taking Half A every Tuesday at 7pm
 * for eight weeks. It's just a label tying the weekly Booking rows together,
 * so they can be shown and cancelled as one; each week is still an ordinary
 * Booking with all the usual protections.
 */
@Entity
@Table(name = "booking_series")
public class BookingSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser bookedBy;

    private LocalDateTime createdAt;

    protected BookingSeries() {
    }

    public BookingSeries(AppUser bookedBy) {
        this.bookedBy = bookedBy;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getBookedBy() {
        return bookedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
