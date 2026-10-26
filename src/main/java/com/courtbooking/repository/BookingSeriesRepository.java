package com.courtbooking.repository;

import com.courtbooking.model.BookingSeries;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSeriesRepository extends JpaRepository<BookingSeries, Long> {
}
