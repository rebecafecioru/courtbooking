package com.courtbooking.controller;

import com.courtbooking.config.AppUserPrincipal;
import com.courtbooking.dto.CourtDtos.CreateCourtRequest;
import com.courtbooking.dto.CourtDtos.CourtResponse;
import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.model.Court;
import com.courtbooking.model.Venue;
import com.courtbooking.repository.CourtRepository;
import com.courtbooking.repository.VenueRepository;
import com.courtbooking.service.OrgAccessService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courts")
public class CourtController {

    private final CourtRepository courtRepository;
    private final VenueRepository venueRepository;
    private final OrgAccessService orgAccessService;

    public CourtController(CourtRepository courtRepository, VenueRepository venueRepository,
                           OrgAccessService orgAccessService) {
        this.courtRepository = courtRepository;
        this.venueRepository = venueRepository;
        this.orgAccessService = orgAccessService;
    }

    @GetMapping
    public List<CourtResponse> getAll(
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String sportType) {
        List<Court> courts;
        if (venueId != null) {
            courts = courtRepository.findByVenueId(venueId);
        } else if (sportType != null) {
            courts = courtRepository.findBySportTypeContainingIgnoreCase(sportType);
        } else {
            courts = courtRepository.findAll();
        }
        return courts.stream().map(CourtResponse::from).toList();
    }

    @PostMapping("/admin")
    public CourtResponse create(@Valid @RequestBody CreateCourtRequest request,
                                @AuthenticationPrincipal AppUserPrincipal admin) {
        orgAccessService.checkCanManageVenue(admin, request.venueId());
        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new ResourceNotFoundException("Venue " + request.venueId() + " not found"));
        Court court = new Court(request.name(), request.sportType(), request.capacity(), venue);
        return CourtResponse.from(courtRepository.save(court));
    }
}