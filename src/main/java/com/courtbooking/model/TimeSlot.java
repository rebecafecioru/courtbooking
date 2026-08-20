package com.courtbooking.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents one bookable window on a court, e.g. Court 1, 18:00-19:00.
 * <p>
 * Concurrency design: this entity carries a @Version column. When two requests
 * race to book the same slot, the second write to commit will fail with an
 * OptimisticLockException because the version it read is stale. The service
 * layer catches that and turns it into a clean 409 Conflict instead of letting
 * both bookings succeed. This is the primary defence; a DB-level unique
 * constraint on Booking.timeSlot is the backup defence (see Booking).
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
