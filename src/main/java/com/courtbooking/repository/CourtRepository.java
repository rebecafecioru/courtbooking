package com.courtbooking.repository;

import com.courtbooking.model.Court;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    // Locks a court row. Used on the top-level court of a hall to serialise
    // everything that reads or changes slots across the hall, including
    // slots that don't exist yet (see BookingService).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Court c where c.id = :id")
    Optional<Court> findByIdForUpdate(@Param("id") Long id);
}
