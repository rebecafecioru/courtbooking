package com.courtbooking.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents one bookable window on a court, e.g. Court 1, 18:00-19:00.
 * <p>
 * Concurrency design: the primary defence against double booking is the
 * pessimistic locking in BookingService, which locks the hall and every slot
 * that could clash before changing any of them. The @Version column here is a
 * backstop: if a slot were ever changed outside that locked path, the stale
 * write would fail with an OptimisticLockException, which
 * GlobalExceptionHandler turns into a 409 Conflict. The unique constraint on
 * Booking.timeSlot is the last line (see Booking).
 */
@Entity
@Table(name = "time_slot", indexes = {
        @Index(name = "idx_court_start", columnList = "court_id, startTime")
})
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private SlotStatus status = SlotStatus.AVAILABLE;

    @Version
    private Long version;

    protected TimeSlot() {
    }

    public TimeSlot(Court court, LocalDateTime startTime, LocalDateTime endTime) {
        this.court = court;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = SlotStatus.AVAILABLE;
    }

    public Long getId() {
        return id;
    }

    public Court getCourt() {
        return court;
    }

    public void setCourt(Court court) {
        this.court = court;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public SlotStatus getStatus() {
        return status;
    }

    public void setStatus(SlotStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }
}
