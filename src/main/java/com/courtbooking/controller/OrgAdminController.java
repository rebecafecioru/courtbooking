package com.courtbooking.controller;

import com.courtbooking.config.AppUserPrincipal;
import com.courtbooking.dto.OrgAdminDtos.AssignOrganisationRequest;
import com.courtbooking.dto.OrgAdminDtos.CreateOrgAdminRequest;
import com.courtbooking.dto.OrgAdminDtos.OrgAdminResponse;
import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.model.AppUser;
import com.courtbooking.model.Organisation;
import com.courtbooking.model.Role;
import com.courtbooking.repository.AppUserRepository;
import com.courtbooking.repository.OrganisationRepository;
import com.courtbooking.service.OrgAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

// The only way (besides DevDataSeeder) to make an ORG_ADMIN. SecurityConfig
// already limits these paths to SUPER_ADMIN; the service check is a second guard.
@RestController
@RequestMapping("/api/admin")
public class OrgAdminController {

    private final AppUserRepository appUserRepository;
    private final OrganisationRepository organisationRepository;
    private final OrgAccessService orgAccessService;
    private final PasswordEncoder passwordEncoder;

    public OrgAdminController(AppUserRepository appUserRepository, OrganisationRepository organisationRepository,
                              OrgAccessService orgAccessService, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.organisationRepository = organisationRepository;
        this.orgAccessService = orgAccessService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/org-admins")
    public ResponseEntity<OrgAdminResponse> createOrgAdmin(@Valid @RequestBody CreateOrgAdminRequest request,
                                                           @AuthenticationPrincipal AppUserPrincipal admin) {
        orgAccessService.checkIsSuperAdmin(admin);
        Organisation org = findOrganisation(request.organisationId());
        if (appUserRepository.existsByEmail(request.email())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        AppUser user = new AppUser(request.fullName(), request.email(),
                passwordEncoder.encode(request.password()), Role.ORG_ADMIN);
        user.setOrganisation(org);
        appUserRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrgAdminResponse.from(user, org));
    }

    // Makes an existing user the ORG_ADMIN of the given organisation (or moves
    // an org admin to another one). Refuses to demote a SUPER_ADMIN.
    @PatchMapping("/users/{id}/organisation")
    public ResponseEntity<OrgAdminResponse> assignOrganisation(@PathVariable Long id,
                                                               @Valid @RequestBody AssignOrganisationRequest request,
                                                               @AuthenticationPrincipal AppUserPrincipal admin) {
        orgAccessService.checkIsSuperAdmin(admin);
        AppUser user = appUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
        Organisation org = findOrganisation(request.organisationId());
        if (user.getRole() == Role.SUPER_ADMIN) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        user.setRole(Role.ORG_ADMIN);
        user.setOrganisation(org);
        appUserRepository.save(user);
        return ResponseEntity.ok(OrgAdminResponse.from(user, org));
    }

    private Organisation findOrganisation(Long organisationId) {
        return organisationRepository.findById(organisationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organisation " + organisationId + " not found"));
    }
}
