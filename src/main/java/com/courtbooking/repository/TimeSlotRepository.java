package com.courtbooking.repository;

import com.courtbooking.model.SlotStatus;
import com.courtbooking.model.TimeSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    List<TimeSlot> findByCourtIdAndStartTimeBetween(Long courtId, LocalDateTime from, LocalDateTime to);

    List<TimeSlot> findByCourtIdAndStatusAndStartTimeBetween(
            Long courtId, SlotStatus status, LocalDateTime from, LocalDateTime to);

    // Pessimistic lock: used inside BookingService so that, under load, a second
    // transaction trying to book the same slot blocks until the first commits,
    // rather than both reading AVAILABLE and racing. Combined with the @Version
    // field on TimeSlot and the unique constraint on Booking, this gives three
    // independent layers against double-booking.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TimeSlot t where t.id = :id")
    Optional<TimeSlot> findByIdForUpdate(@Param("id") Long id);
}
