package com.courtbooking.service;

import com.courtbooking.dto.CreateSlotsRequest;
import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class TimeSlotServiceTest {

    @Autowired private TimeSlotService timeSlotService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;

    private Court court;
    // A date well in the future, so it never clashes with seeded demo slots
    private final LocalDate day = LocalDate.now().plusYears(1);

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Gen Uni", "d", "g@example.com"));
        Venue venue = venueRepository.save(new Venue("Gen Venue", "addr", org));
        court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
    }

    private CreateSlotsRequest nineToTwelve(int minutes) {
        return new CreateSlotsRequest(court.getId(), day, day, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(12, 0), minutes);
    }

    private long slotsOnCourt() {
        return timeSlotRepository.findByCourtIdAndStartTimeBetween(
                court.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay()).size();
    }

    @Test
    void generatesSlotsOfTheRequestedLength() {
        List<TimeSlot> created = timeSlotService.generateSlots(nineToTwelve(60));

        assertEquals(3, created.size());
        assertEquals(day.atTime(9, 0), created.get(0).getStartTime());
        assertEquals(day.atTime(12, 0), created.get(2).getEndTime());
    }

    @Test
    void generatingTheSameSlotsTwiceDoesNotCreateDuplicates() {
        timeSlotService.generateSlots(nineToTwelve(60));

        List<TimeSlot> second = timeSlotService.generateSlots(nineToTwelve(60));

        assertTrue(second.isEmpty());
        assertEquals(3, slotsOnCourt());
    }

    @Test
    void newSlotsThatPartlyOverlapExistingOnesAreSkipped() {
        // 09:00-10:00, 10:00-11:00, 11:00-12:00 already exist
        timeSlotService.generateSlots(nineToTwelve(60));

        // 90-minute slots would be 09:00-10:30 and 10:30-12:00: both overlap
        List<TimeSlot> created = timeSlotService.generateSlots(nineToTwelve(90));

        assertTrue(created.isEmpty());
        assertEquals(3, slotsOnCourt());
    }

    @Test
    void onlyTheMissingSlotsAreFilledIn() {
        timeSlotRepository.save(new TimeSlot(court, day.atTime(10, 0), day.atTime(11, 0)));

        List<TimeSlot> created = timeSlotService.generateSlots(nineToTwelve(60));

        assertEquals(List.of(day.atTime(9, 0), day.atTime(11, 0)),
                created.stream().map(TimeSlot::getStartTime).toList());
    }
}
