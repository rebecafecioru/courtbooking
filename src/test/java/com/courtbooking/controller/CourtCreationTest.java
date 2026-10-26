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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Creating courts, including halves of a hall (a court with a parent)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CourtCreationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Venue venue;
    private Venue otherVenue;
    private Court fullHall;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Court Uni", "d", "c@example.com"));
        venue = venueRepository.save(new Venue("Main Venue", "addr", org));
        otherVenue = venueRepository.save(new Venue("Other Venue", "addr", org));
        fullHall = courtRepository.save(new Court("Hall (Full)", "Volleyball", 12, venue));
        AppUser admin = new AppUser("Admin", "admin@courts.test", passwordEncoder.encode("pw"), Role.ORG_ADMIN);
        admin.setOrganisation(org);
        appUserRepository.save(admin);
    }

    private ResultActions createCourt(String name, Long venueId, Long parentCourtId) throws Exception {
        return mockMvc.perform(post("/api/courts/admin")
                .header("Authorization", "Bearer " + jwtUtil.generateToken("admin@courts.test"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\", \"sportType\": \"Volleyball\", \"capacity\": 6, "
                        + "\"venueId\": " + venueId + ", \"parentCourtId\": " + parentCourtId + "}"));
    }

    @Test
    void aCourtCanBeAHalfOfAHall() throws Exception {
        createCourt("Hall - Half A", venue.getId(), fullHall.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentCourtId").value(fullHall.getId()));
    }

    @Test
    void aCourtWithoutAParentStandsAlone() throws Exception {
        createCourt("Squash 1", venue.getId(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentCourtId").doesNotExist());
    }

    @Test
    void theParentMustBeAtTheSameVenue() throws Exception {
        createCourt("Half elsewhere", otherVenue.getId(), fullHall.getId())
                .andExpect(status().isBadRequest());
    }

    @Test
    void hallsSplitOnlyOneLevelDeep() throws Exception {
        Court half = courtRepository.save(new Court("Hall - Half A", "Volleyball", 6, venue, fullHall));

        createCourt("Quarter", venue.getId(), half.getId())
                .andExpect(status().isBadRequest());
    }
}
