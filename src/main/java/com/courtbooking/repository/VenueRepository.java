package com.courtbooking.repository;

import com.courtbooking.model.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    List<Venue> findByOrganisationId(Long organisationId);

    // Which organisation owns this venue (for admin access checks)
    @Query("select v.organisation.id from Venue v where v.id = :id")
    Optional<Long> findOrganisationIdById(@Param("id") Long id);
}
