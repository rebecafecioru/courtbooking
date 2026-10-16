package com.courtbooking.repository;

import com.courtbooking.model.SlotStatus;
import com.courtbooking.model.TimeSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    // Just the court and the time window of a slot, without loading the slot
    // itself (so nothing stale is cached before we take the locks).
    interface SlotWindow {
        Long getCourtId();
        LocalDateTime getStartTime();
        LocalDateTime getEndTime();
    }

    List<TimeSlot> findByCourtIdAndStartTimeBetween(Long courtId, LocalDateTime from, LocalDateTime to);

    List<TimeSlot> findByCourtIdAndStatusAndStartTimeBetween(
            Long courtId, SlotStatus status, LocalDateTime from, LocalDateTime to);

    @Query("select t.court.id as courtId, t.startTime as startTime, t.endTime as endTime "
            + "from TimeSlot t where t.id = :id")
    Optional<SlotWindow> findWindowById(@Param("id") Long id);

    // Which organisation owns this slot's court (for admin access checks)
    @Query("select t.court.venue.organisation.id from TimeSlot t where t.id = :id")
    Optional<Long> findOrganisationIdById(@Param("id") Long id);

    // Ids of the slots on the given courts covering exactly this time window.
    @Query("select t.id from TimeSlot t where t.court.id in :courtIds "
            + "and t.startTime = :start and t.endTime = :end")
    List<Long> findIdsByCourtsAndTime(@Param("courtIds") Collection<Long> courtIds,
                                      @Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    boolean existsByCourtIdInAndStartTimeAndEndTimeAndStatus(
            Collection<Long> courtIds, LocalDateTime start, LocalDateTime end, SlotStatus status);

    // Locks a whole group of slots at once, always in id order, so two
    // bookings that touch the same group can never deadlock each other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TimeSlot t where t.id in :ids order by t.id")
    List<TimeSlot> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}