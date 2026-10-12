package com.courtbooking.repository;

import com.courtbooking.model.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationRepository extends JpaRepository<Organisation, Long> {
}
