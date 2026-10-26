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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private Long nextWeekSlotId;
    private Long userId;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Test Uni", "d", "t@example.com"));
        Venue venue = venueRepository.save(new Venue("Sports Hall", "addr", org));
        Court court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
        LocalDateTime start = LocalDateTime.now().plusDays(1).withSecond(0).withNano(0);
        slotId = timeSlotRepository.save(new TimeSlot(court, start, start.plusHours(1))).getId();
        nextWeekSlotId = timeSlotRepository.save(
                new TimeSlot(court, start.plusWeeks(1), start.plusWeeks(1).plusHours(1))).getId();
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

    @Test
    void weeklySeriesCanBePreviewedBookedAndCancelled() throws Exception {
        String auth = "Bearer " + jwtUtil.generateToken("me@test.com");

        mockMvc.perform(get("/api/bookings/series/preview")
                        .param("firstSlotId", slotId.toString()).param("weeks", "3")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FREE"))
                .andExpect(jsonPath("$[1].timeSlotId").value(nextWeekSlotId))
                .andExpect(jsonPath("$[2].status").value("NO_SLOT"));

        String body = mockMvc.perform(post("/api/bookings/series")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeSlotIds\": [" + slotId + ", " + nextWeekSlotId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookings", hasSize(2)))
                .andExpect(jsonPath("$.bookings[0].seriesId").isNumber())
                .andReturn().getResponse().getContentAsString();
        String seriesId = body.replaceAll(".*\"seriesId\":(\\d+).*", "$1");

        // Booking the same weeks again is refused, all or nothing
        mockMvc.perform(post("/api/bookings/series")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeSlotIds\": [" + slotId + ", " + nextWeekSlotId + "]}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/bookings/series/{id}", seriesId).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledWeeks").value(2));
    }

    @Test
    void slotListMarksTheViewersOwnBookingWhenLoggedIn() throws Exception {
        bookingService.bookSlot(slotId, userId);
        Long courtId = timeSlotRepository.findById(slotId).orElseThrow().getCourt().getId();
        String from = LocalDateTime.now().withNano(0).toString();
        String to = LocalDateTime.now().plusDays(14).withNano(0).toString();

        mockMvc.perform(get("/api/courts/{id}/slots", courtId)
                        .param("from", from).param("to", to).param("availableOnly", "false")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken("me@test.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + slotId + ")].yours").value(true))
                .andExpect(jsonPath("$[?(@.id == " + nextWeekSlotId + ")].yours").value(false));

        // Not logged in: nothing is anyone's
        mockMvc.perform(get("/api/courts/{id}/slots", courtId)
                        .param("from", from).param("to", to).param("availableOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + slotId + ")].yours").value(false));
    }
}
