package com.courtbooking.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A user's place in the queue for a slot that is currently taken.
 * <p>
 * Queue order is by id: ids come from an identity column, so a later join
 * always gets a bigger id, and unlike joinedAt two entries can never tie.
 * The unique constraint stops the same user queueing twice for one slot.
 */
@Entity
@Table(name = "waitlist_entry", uniqueConstraints = @UniqueConstraint(columnNames = {"time_slot_id", "user_id"}))
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", nullable = false)
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    private LocalDateTime joinedAt;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(TimeSlot timeSlot, AppUser user) {
        this.timeSlot = timeSlot;
        this.user = user;
        this.joinedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public TimeSlot getTimeSlot() {
        return timeSlot;
    }

    public AppUser getUser() {
        return user;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
