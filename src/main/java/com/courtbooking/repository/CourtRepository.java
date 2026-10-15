package com.courtbooking.repository;

import com.courtbooking.model.Court;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourtRepository extends JpaRepository<Court, Long> {

    List<Court> findByVenueId(Long venueId);

    // "Contains", so searching for "volleyball" also finds a hall whose
    // sport type is "Volleyball, Badminton, Basketball, ..."
    List<Court> findBySportTypeContainingIgnoreCase(String sportType);

    // The halves of a bigger space (empty for ordinary courts)
    List<Court> findByParentId(Long parentId);
}