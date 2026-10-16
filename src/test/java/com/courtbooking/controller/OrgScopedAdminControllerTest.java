package com.courtbooking.controller;

import com.courtbooking.config.JwtUtil;
import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// An ORG_ADMIN may only manage their own organisation; a SUPER_ADMIN may manage any.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrgScopedAdminControllerTest {

    private static final String OWN_ADMIN = "own@orgscope.test";
    private static final String SUPER_ADMIN = "super@orgscope.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long ownOrgId, otherOrgId;
    private Long ownVenueId, otherVenueId;
    private Long ownCourtId, otherCourtId;
    private Long ownSlotId, otherSlotId;

    @BeforeEach
    void setUp() {
        Organisation own = organisationRepository.save(new Organisation("Own Uni", "d", "own@example.com"));
        Organisation other = organisationRepository.save(new Organisation("Other Uni", "d", "other@example.com"));
        ownOrgId = own.getId();
        otherOrgId = other.getId();

        Venue ownVenue = venueRepository.save(new Venue("Own Venue", "addr", own));
        Venue otherVenue = venueRepository.save(new Venue("Other Venue", "addr", other));
        ownVenueId = ownVenue.getId();
        otherVenueId = otherVenue.getId();

        Court ownCourt = courtRepository.save(new Court("Own Court", "Tennis", 4, ownVenue));
        Court otherCourt = courtRepository.save(new Court("Other Court", "Tennis", 4, otherVenue));
        ownCourtId = ownCourt.getId();
        otherCourtId = otherCourt.getId();

        LocalDateTime start = LocalDateTime.now().plusDays(1);
        ownSlotId = timeSlotRepository.save(new TimeSlot(ownCourt, start, start.plusHours(1))).getId();
        otherSlotId = timeSlotRepository.save(new TimeSlot(otherCourt, start, start.plusHours(1))).getId();

        AppUser ownAdmin = new AppUser("Own Admin", OWN_ADMIN, passwordEncoder.encode("pw"), Role.ORG_ADMIN);
        ownAdmin.setOrganisation(own);
        appUserRepository.save(ownAdmin);
        appUserRepository.save(new AppUser("Super", SUPER_ADMIN, passwordEncoder.encode("pw"), Role.SUPER_ADMIN));
    }

    private String bearer(String email) {
        return "Bearer " + jwtUtil.generateToken(email);
    }

    private void createVenue(String email, Long orgId, ResultMatcher expected) throws Exception {
        mockMvc.perform(post("/api/venues/admin").header("Authorization", bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Venue\",\"address\":\"addr\",\"organisationId\":" + orgId + "}"))
                .andExpect(expected);
    }

    private void createCourt(String email, Long venueId, ResultMatcher expected) throws Exception {
        mockMvc.perform(post("/api/courts/admin").header("Authorization", bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Court\",\"sportType\":\"Tennis\",\"capacity\":4,\"venueId\":"
                                + venueId + "}"))
                .andExpect(expected);
    }

    private void generateSlots(String email, Long courtId, ResultMatcher expected) throws Exception {
        LocalDate day = LocalDate.now().plusDays(10);
        mockMvc.perform(post("/api/admin/slots/generate").header("Authorization", bearer(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courtId\":" + courtId + ",\"fromDate\":\"" + day + "\",\"toDate\":\"" + day
                                + "\",\"daysOfWeek\":[\"" + day.getDayOfWeek() + "\"],"
                                + "\"dailyStartTime\":\"09:00\",\"dailyEndTime\":\"11:00\",\"slotDurationMinutes\":60}"))
                .andExpect(expected);
    }

    private void blockAndUnblock(String email, Long slotId, ResultMatcher expected) throws Exception {
        mockMvc.perform(patch("/api/admin/slots/{id}/block", slotId).header("Authorization", bearer(email)))
                .andExpect(expected);
        mockMvc.perform(patch("/api/admin/slots/{id}/unblock", slotId).header("Authorization", bearer(email)))
                .andExpect(expected);
    }

    // --- own organisation: allowed ---

    @Test
    void orgAdminCanCreateVenueInOwnOrganisation() throws Exception {
        createVenue(OWN_ADMIN, ownOrgId, status().isOk());
    }

    @Test
    void orgAdminCanCreateCourtAtOwnVenue() throws Exception {
        createCourt(OWN_ADMIN, ownVenueId, status().isOk());
    }

    @Test
    void orgAdminCanGenerateSlotsOnOwnCourt() throws Exception {
        generateSlots(OWN_ADMIN, ownCourtId, status().isOk());
    }

    @Test
    void orgAdminCanBlockAndUnblockOwnSlot() throws Exception {
        blockAndUnblock(OWN_ADMIN, ownSlotId, status().isNoContent());
    }

    // --- another organisation: 403, and nothing changes ---

    @Test
    void orgAdminCannotCreateVenueInAnotherOrganisation() throws Exception {
        createVenue(OWN_ADMIN, otherOrgId, status().isForbidden());
        assertThat(venueRepository.findByOrganisationId(otherOrgId)).hasSize(1);
    }

    @Test
    void orgAdminCannotCreateCourtAtAnotherOrganisationsVenue() throws Exception {
        createCourt(OWN_ADMIN, otherVenueId, status().isForbidden());
        assertThat(courtRepository.findByVenueId(otherVenueId)).hasSize(1);
    }

    @Test
    void orgAdminCannotGenerateSlotsOnAnotherOrganisationsCourt() throws Exception {
        generateSlots(OWN_ADMIN, otherCourtId, status().isForbidden());
        assertThat(timeSlotRepository.findByCourtIdAndStartTimeBetween(otherCourtId,
                LocalDateTime.now().plusDays(9), LocalDateTime.now().plusDays(12))).isEmpty();
    }

    @Test
    void orgAdminCannotBlockOrUnblockAnotherOrganisationsSlot() throws Exception {
        blockAndUnblock(OWN_ADMIN, otherSlotId, status().isForbidden());
        assertThat(timeSlotRepository.findById(otherSlotId).orElseThrow().getStatus()).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void orgAdminWithoutAnOrganisationCannotManageAnything() throws Exception {
        appUserRepository.save(new AppUser("Loose", "loose@orgscope.test", passwordEncoder.encode("pw"), Role.ORG_ADMIN));
        createVenue("loose@orgscope.test", ownOrgId, status().isForbidden());
        blockAndUnblock("loose@orgscope.test", ownSlotId, status().isForbidden());
    }

    // --- super admin: allowed everywhere ---

    @Test
    void superAdminCanManageAnyOrganisation() throws Exception {
        createVenue(SUPER_ADMIN, otherOrgId, status().isOk());
        createCourt(SUPER_ADMIN, otherVenueId, status().isOk());
        generateSlots(SUPER_ADMIN, otherCourtId, status().isOk());
        blockAndUnblock(SUPER_ADMIN, otherSlotId, status().isNoContent());
    }

    @Test
    void missingTargetIsStillNotFound() throws Exception {
        blockAndUnblock(OWN_ADMIN, -1L, status().isNotFound());
    }
}
