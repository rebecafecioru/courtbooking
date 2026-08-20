package com.courtbooking.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A confirmed booking of a single TimeSlot.
 * <p>
 * The unique constraint on time_slot_id is the second layer of protection
 * against double-booking: even if the optimistic lock in TimeSlotService
 * were ever bypassed, the database itself physically cannot store two
 * Booking rows pointing at the same slot.
 */
@Entity
@Table(name = "booking", uniqueConstraints = @UniqueConstraint(columnNames = "time_slot_id"))
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", nullable = false, unique = true)
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser bookedBy;

    private LocalDateTime bookedAt;

    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.CONFIRMED;

    protected Booking() {
    }

    public Booking(TimeSlot timeSlot, AppUser bookedBy) {
        this.timeSlot = timeSlot;
        this.bookedBy = bookedBy;
        this.bookedAt = LocalDateTime.now();
        this.status = BookingStatus.CONFIRMED;
    }

    public Long getId() {
        return id;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public AppUser getBookedBy() {
        return bookedBy;
    }

    public LocalDateTime getBookedAt() {
        return bookedAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public enum BookingStatus {
        CONFIRMED,
        CANCELLED
    }
}
