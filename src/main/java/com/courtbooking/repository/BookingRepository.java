package com.courtbooking.repository;

import com.courtbooking.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByBookedById(Long userId);
    Optional<Booking> findByTimeSlotId(Long timeSlotId);

    // Fetches the slot, court and venue in the same query, so building a
    // BookingResponse for each row doesn't trigger extra lazy-loading queries.
    @Query("select b from Booking b "
            + "join fetch b.timeSlot t join fetch t.court c join fetch c.venue "
            + "join fetch b.bookedBy "
            + "where b.bookedBy.id = :userId order by t.startTime")
    List<Booking> findByUserWithDetails(@Param("userId") Long userId);
}
