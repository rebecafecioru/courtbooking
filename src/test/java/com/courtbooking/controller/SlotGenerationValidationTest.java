package com.courtbooking.controller;

import com.courtbooking.config.JwtUtil;
import com.courtbooking.dto.CreateSlotsRequest;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Requests that make no sense, or would create far too many slots, are refused with a 400.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SlotGenerationValidationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long courtId;
    // Far enough ahead that none of these slots are in the past
    private final LocalDate from = LocalDate.now().plusYears(1);

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Limits Uni", "d", "lim@example.com"));
        Venue venue = venueRepository.save(new Venue("Limits Venue", "addr", org));
        courtId = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue)).getId();
        AppUser admin = new AppUser("Admin", "admin@limits.test", passwordEncoder.encode("pw"), Role.ORG_ADMIN);
        admin.setOrganisation(org);
        appUserRepository.save(admin);
    }

    private ResultActions generate(LocalDate fromDate, LocalDate toDate, String days,
                                   String start, String end, int minutes) throws Exception {
        String json = "{\"courtId\":" + courtId
                + ",\"fromDate\":\"" + fromDate + "\",\"toDate\":\"" + toDate + "\""
                + ",\"daysOfWeek\":" + days
                + ",\"dailyStartTime\":\"" + start + "\",\"dailyEndTime\":\"" + end + "\""
                + ",\"slotDurationMinutes\":" + minutes + "}";
        return mockMvc.perform(post("/api/admin/slots/generate")
                .header("Authorization", "Bearer " + jwtUtil.generateToken("admin@limits.test"))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static final String WEEKDAYS = "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\"]";

    private long slotsOnCourt() {
        return timeSlotRepository.findByCourtIdAndStartTimeBetween(
                courtId, from.minusYears(1).atStartOfDay(), from.plusYears(2).atStartOfDay()).size();
    }

    @Test
    void aSensibleRequestStillWorks() throws Exception {
        generate(from, from.plusDays(6), WEEKDAYS, "09:00", "12:00", 60).andExpect(status().isOk());

        assertThat(slotsOnCourt()).isEqualTo(15); // 5 weekdays x 3 hours
    }

    @Test
    void theMaximumRangeIsAllowed() throws Exception {
        generate(from, from.plusDays(CreateSlotsRequest.MAX_DAYS - 1), WEEKDAYS, "09:00", "10:00", 60)
                .andExpect(status().isOk());
    }

    @Test
    void endDateBeforeStartDateIsRejected() throws Exception {
        generate(from, from.minusDays(1), WEEKDAYS, "09:00", "12:00", 60)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateOrderValid").exists());
    }

    @Test
    void aRangeLongerThanTheLimitIsRejected() throws Exception {
        generate(from, from.plusDays(CreateSlotsRequest.MAX_DAYS), WEEKDAYS, "09:00", "12:00", 60)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateRangeWithinLimit").exists());

        assertThat(slotsOnCourt()).isZero();
    }

    @Test
    void endTimeBeforeStartTimeIsRejected() throws Exception {
        generate(from, from.plusDays(6), WEEKDAYS, "12:00", "09:00", 60)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dailyTimeOrderValid").exists());
    }

    @Test
    void aSlotLongerThanTheDailyWindowIsRejected() throws Exception {
        generate(from, from.plusDays(6), WEEKDAYS, "09:00", "10:00", 90)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.slotLengthWithinDay").exists());
    }

    @Test
    void noDaysOfTheWeekIsRejected() throws Exception {
        generate(from, from.plusDays(6), "[]", "09:00", "12:00", 60)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.daysOfWeek").exists());
    }

    @Test
    void slotsShorterThanFifteenMinutesAreRejected() throws Exception {
        generate(from, from.plusDays(6), WEEKDAYS, "09:00", "12:00", 10)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.slotDurationMinutes").exists());
    }
}
