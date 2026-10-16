package com.courtbooking.repository;

import com.courtbooking.model.Court;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CourtRepository extends JpaRepository<Court, Long> {

    List<Court> findByVenueId(Long venueId);

    // "Contains", so searching for "volleyball" also finds a hall whose
    // sport type is "Volleyball, Badminton, Basketball, ..."
    List<Court> findBySportTypeContainingIgnoreCase(String sportType);

    // The halves of a bigger space (empty for ordinary courts)
    List<Court> findByParentId(Long parentId);

    // Which organisation owns this court (for admin access checks)
    @Query("select c.venue.organisation.id from Court c where c.id = :id")
    Optional<Long> findOrganisationIdById(@Param("id") Long id);
}
