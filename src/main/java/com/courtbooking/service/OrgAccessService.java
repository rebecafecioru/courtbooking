package com.courtbooking.service;

import com.courtbooking.config.AppUserPrincipal;
import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.model.Role;
import com.courtbooking.repository.CourtRepository;
import com.courtbooking.repository.OrganisationRepository;
import com.courtbooking.repository.TimeSlotRepository;
import com.courtbooking.repository.VenueRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * SecurityConfig only checks that a caller is an admin. This checks WHICH
 * organisation they may manage: an ORG_ADMIN only their own, a SUPER_ADMIN
 * any. Called by the admin endpoints before they change anything.
 */
@Service
public class OrgAccessService {

    private final OrganisationRepository organisationRepository;
    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final TimeSlotRepository timeSlotRepository;

    public OrgAccessService(OrganisationRepository organisationRepository, VenueRepository venueRepository,
                            CourtRepository courtRepository, TimeSlotRepository timeSlotRepository) {
        this.organisationRepository = organisationRepository;
        this.venueRepository = venueRepository;
        this.courtRepository = courtRepository;
        this.timeSlotRepository = timeSlotRepository;
    }

    public void checkCanManageOrganisation(AppUserPrincipal admin, Long organisationId) {
        if (!organisationRepository.existsById(organisationId)) {
            throw new ResourceNotFoundException("Organisation " + organisationId + " not found");
        }
        check(admin, organisationId);
    }

    public void checkCanManageVenue(AppUserPrincipal admin, Long venueId) {
        check(admin, venueRepository.findOrganisationIdById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Venue " + venueId + " not found")));
    }

    public void checkCanManageCourt(AppUserPrincipal admin, Long courtId) {
        check(admin, courtRepository.findOrganisationIdById(courtId)
                .orElseThrow(() -> new ResourceNotFoundException("Court " + courtId + " not found")));
    }

    public void checkCanManageSlot(AppUserPrincipal admin, Long slotId) {
        check(admin, timeSlotRepository.findOrganisationIdById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot " + slotId + " not found")));
    }

    private void check(AppUserPrincipal admin, Long organisationId) {
        if (admin.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        // An ORG_ADMIN with no organisation set can manage nothing
        if (admin.getRole() != Role.ORG_ADMIN || !organisationId.equals(admin.getOrganisationId())) {
            throw new AccessDeniedException("You can only manage your own organisation's venues, courts and slots");
        }
    }
}
