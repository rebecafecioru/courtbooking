package com.courtbooking.repository;

import com.courtbooking.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByBookedById(Long userId);
    Optional<Booking> findByTimeSlotId(Long timeSlotId);
}
