package com.courtbooking.repository;

import com.courtbooking.model.Court;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourtRepository extends JpaRepository<Court, Long> {
    List<Court> findByVenueId(Long venueId);
    List<Court> findBySportTypeIgnoreCase(String sportType);
}
