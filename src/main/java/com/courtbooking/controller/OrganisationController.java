package com.courtbooking.controller;

import com.courtbooking.dto.OrganisationResponse;
import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.model.Organisation;
import com.courtbooking.repository.OrganisationRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organisations")
public class OrganisationController {

    private final OrganisationRepository organisationRepository;

    public OrganisationController(OrganisationRepository organisationRepository) {
        this.organisationRepository = organisationRepository;
    }

    @GetMapping
    public List<OrganisationResponse> getAll() {
        return organisationRepository.findAll().stream()
                .map(OrganisationResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public OrganisationResponse getOne(@PathVariable Long id) {
        Organisation org = organisationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation " + id + " not found"));
        return OrganisationResponse.from(org);
    }

    @PostMapping("/admin")
    public ResponseEntity<OrganisationResponse> create(@Valid @RequestBody Organisation organisation) {
        return ResponseEntity.ok(OrganisationResponse.from(organisationRepository.save(organisation)));
    }
}