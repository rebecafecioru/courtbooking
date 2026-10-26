package com.courtbooking.controller;

import com.courtbooking.config.AppUserPrincipal;
import com.courtbooking.dto.CourtDtos.CreateCourtRequest;
import com.courtbooking.dto.CourtDtos.CourtResponse;
import com.courtbooking.exception.BadRequestException;
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
        Court court = request.parentCourtId() == null
                ? new Court(request.name(), request.sportType(), request.capacity(), venue)
                : new Court(request.name(), request.sportType(), request.capacity(), venue,
                        findParent(request.parentCourtId(), venue));
        return CourtResponse.from(courtRepository.save(court));
    }

    // The court a new one is part of (e.g. the full hall for a half). It has to
    // be at the same venue, and halls split only one level deep: BookingService
    // works out overlaps (and what to lock) as a court's parent and children.
    private Court findParent(Long parentCourtId, Venue venue) {
        Court parent = courtRepository.findById(parentCourtId)
                .orElseThrow(() -> new ResourceNotFoundException("Court " + parentCourtId + " not found"));
        if (!parent.getVenue().getId().equals(venue.getId())) {
            throw new BadRequestException("A court can only be part of another court at the same venue.");
        }
        if (parent.getParent() != null) {
            throw new BadRequestException(
                    "That court is already part of another one. A hall can only be split one level deep.");
        }
        return parent;
    }
}