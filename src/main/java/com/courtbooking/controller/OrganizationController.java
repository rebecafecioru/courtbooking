package com.courtbooking.controller;

import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.model.Organization;
import com.courtbooking.repository.OrganizationRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {
    private final OrganizationRepository organizationRepository;

    public OrganizationController(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @GetMapping
    public List<Organization> getAll() {
        return organizationRepository.findAll();
    }

    @GetMapping("/{id}")
    public Organization getOne(@PathVariable Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization " + id + " not found"));
    }

    @PostMapping("/admin")
    public ResponseEntity<Organization> create(@Valid @RequestBody Organization organization) {
        return ResponseEntity.ok(organizationRepository.save(organization));
    }
}
