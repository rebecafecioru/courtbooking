package com.courtbooking.controller;

import com.courtbooking.config.AppUserPrincipal;
import com.courtbooking.dto.VenueDtos.CreateVenueRequest;
import com.courtbooking.dto.VenueDtos.VenueResponse;
import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.model.Organisation;
import com.courtbooking.model.Venue;
import com.courtbooking.repository.OrganisationRepository;
import com.courtbooking.repository.VenueRepository;
import com.courtbooking.service.OrgAccessService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueRepository venueRepository;
    private final OrganisationRepository organisationRepository;
    private final OrgAccessService orgAccessService;

    public VenueController(VenueRepository venueRepository, OrganisationRepository organisationRepository,
                           OrgAccessService orgAccessService) {
        this.venueRepository = venueRepository;
        this.organisationRepository = organisationRepository;
        this.orgAccessService = orgAccessService;
    }

    @GetMapping
    public List<VenueResponse> getAll(@RequestParam(required = false) Long organisationId) {
        List<Venue> venues = organisationId != null
                ? venueRepository.findByOrganisationId(organisationId)
                : venueRepository.findAll();
        return venues.stream().map(VenueResponse::from).toList();
    }

    @GetMapping("/{id}")
    public VenueResponse getOne(@PathVariable Long id) {
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venue " + id + " not found"));
        return VenueResponse.from(venue);
    }

    @PostMapping("/admin")
    public VenueResponse create(@Valid @RequestBody CreateVenueRequest request,
                                @AuthenticationPrincipal AppUserPrincipal admin) {
        orgAccessService.checkCanManageOrganisation(admin, request.organisationId());
        Organisation org = organisationRepository.findById(request.organisationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organisation " + request.organisationId() + " not found"));
        Venue venue = new Venue(request.name(), request.address(), org);
        return VenueResponse.from(venueRepository.save(venue));
    }
}
