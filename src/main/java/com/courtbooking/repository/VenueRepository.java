package com.courtbooking.repository;

import com.courtbooking.model.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {
    List<Venue> findByOrganisationId(Long organisationId);
}
