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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Goes through the real HTTP layer (security filter, JWT, JSON), unlike the service tests.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingControllerTest {

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
    private Long userId;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Test Uni", "d", "t@example.com"));
        Venue venue = venueRepository.save(new Venue("Sports Hall", "addr", org));
        Court court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
        slotId = timeSlotRepository.save(new TimeSlot(court,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1))).getId();
        userId = appUserRepository.save(
                new AppUser("Me", "me@test.com", passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    @Test
    void myBookingsReturnsTheLoggedInUsersBookings() throws Exception {
        bookingService.bookSlot(slotId, userId);

        mockMvc.perform(get("/api/bookings/me")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken("me@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].timeSlotId").value(slotId))
                .andExpect(jsonPath("$[0].courtName").value("Court 1"))
                .andExpect(jsonPath("$[0].venueName").value("Sports Hall"));
    }

    @Test
    void myBookingsRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/bookings/me"))
                .andExpect(status().is4xxClientError());
    }
}
