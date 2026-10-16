package com.courtbooking.controller;

import com.courtbooking.config.JwtUtil;
import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import com.courtbooking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSlotControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long slotId;
    private Long playerId;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Admin Uni", "d", "ad@example.com"));
        Venue venue = venueRepository.save(new Venue("Admin Venue", "addr", org));
        Court court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
        slotId = timeSlotRepository.save(new TimeSlot(court,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1))).getId();
        AppUser admin = new AppUser("Admin", "admin@admin.test", passwordEncoder.encode("pw"), Role.ORG_ADMIN);
        admin.setOrganisation(org);
        appUserRepository.save(admin);
        playerId = appUserRepository.save(
                new AppUser("Player", "player@admin.test", passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    private String bearer(String email) {
        return "Bearer " + jwtUtil.generateToken(email);
    }

    @Test
    void adminCanBlockAndUnblockASlot() throws Exception {
        mockMvc.perform(patch("/api/admin/slots/{id}/block", slotId).header("Authorization", bearer("admin@admin.test")))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/api/admin/slots/{id}/unblock", slotId).header("Authorization", bearer("admin@admin.test")))
                .andExpect(status().isNoContent());
    }

    @Test
    void blockingABookedSlotIsAConflict() throws Exception {
        bookingService.bookSlot(slotId, playerId);

        mockMvc.perform(patch("/api/admin/slots/{id}/block", slotId).header("Authorization", bearer("admin@admin.test")))
                .andExpect(status().isConflict());
    }

    @Test
    void ordinaryUsersCannotBlockSlots() throws Exception {
        mockMvc.perform(patch("/api/admin/slots/{id}/block", slotId).header("Authorization", bearer("player@admin.test")))
                .andExpect(status().isForbidden());
    }
}
