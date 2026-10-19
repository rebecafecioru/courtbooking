package com.courtbooking.controller;

import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The public slots endpoint only returns a sensible, limited date range.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SlotBrowsingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;

    private Long courtId;
    private final LocalDateTime from = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Browse Uni", "d", "br@example.com"));
        Venue venue = venueRepository.save(new Venue("Browse Venue", "addr", org));
        Court court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
        courtId = court.getId();
        timeSlotRepository.save(new TimeSlot(court, from.plusHours(9), from.plusHours(10)));
    }

    // No Authorization header: anyone can browse
    private ResultActions browse(LocalDateTime start, LocalDateTime end) throws Exception {
        return mockMvc.perform(get("/api/courts/{id}/slots", courtId)
                .param("from", start.toString()).param("to", end.toString()));
    }

    @Test
    void aWeekOfSlotsCanBeBrowsedWithoutLoggingIn() throws Exception {
        browse(from, from.plusDays(7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void theMaximumRangeIsAllowed() throws Exception {
        browse(from, from.plusDays(TimeSlotController.MAX_BROWSE_DAYS)).andExpect(status().isOk());
    }

    @Test
    void aRangeLongerThanTheLimitIsRejected() throws Exception {
        browse(from, from.plusDays(TimeSlotController.MAX_BROWSE_DAYS).plusMinutes(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void aRangeThatEndsBeforeItStartsIsRejected() throws Exception {
        browse(from, from.minusHours(1)).andExpect(status().isBadRequest());
    }
}
