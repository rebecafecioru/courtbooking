package com.courtbooking.controller;

import com.courtbooking.dto.VenueDtos.CreateVenueRequest;
import com.courtbooking.dto.VenueDtos.VenueResponse;
import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.model.Organization;
import com.courtbooking.model.Venue;
import com.courtbooking.repository.OrganizationRepository;
import com.courtbooking.repository.VenueRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueRepository venueRepository;
    private final OrganizationRepository organizationRepository;

    public VenueController(VenueRepository venueRepository, OrganizationRepository organizationRepository) {
        this.venueRepository = venueRepository;
        this.organizationRepository = organizationRepository;
    }

    @GetMapping
    public List<VenueResponse> getAll(@RequestParam(required = false) Long organizationId) {
        List<Venue> venues = organizationId != null
                ? venueRepository.findByOrganizationId(organizationId)
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
    public VenueResponse create(@Valid @RequestBody CreateVenueRequest request) {
        Organization org = organizationRepository.findById(request.organizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization " + request.organizationId() + " not found"));
        Venue venue = new Venue(request.name(), request.address(), org);
        return VenueResponse.from(venueRepository.save(venue));
    }
}
