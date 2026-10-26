package com.courtbooking.service;

import com.courtbooking.exception.BadRequestException;
import com.courtbooking.exception.SlotNotAvailableException;
import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SeriesBookingTest {

    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // A full hall split into two halves. The halves have a slot at the same
    // time for 4 weeks running; the full hall only for the first 3.
    private final List<TimeSlot> full = new ArrayList<>();
    private final List<TimeSlot> halfA = new ArrayList<>();
    private final List<TimeSlot> halfB = new ArrayList<>();
    private Court halfACourt;
    private Long club;
    private Long other;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Series Uni", "d", "s@example.com"));
        Venue venue = venueRepository.save(new Venue("Series Venue", "addr", org));
        Court fullCourt = courtRepository.save(new Court("Hall (Full)", "Volleyball", 12, venue));
        halfACourt = courtRepository.save(new Court("Hall - Half A", "Volleyball", 6, venue, fullCourt));
        Court halfBCourt = courtRepository.save(new Court("Hall - Half B", "Volleyball", 6, venue, fullCourt));

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(19).withMinute(0).withSecond(0).withNano(0);
        for (int week = 0; week < 4; week++) {
            LocalDateTime s = start.plusWeeks(week);
            if (week < 3) {
                full.add(timeSlotRepository.save(new TimeSlot(fullCourt, s, s.plusHours(1))));
            }
            halfA.add(timeSlotRepository.save(new TimeSlot(halfACourt, s, s.plusHours(1))));
            halfB.add(timeSlotRepository.save(new TimeSlot(halfBCourt, s, s.plusHours(1))));
        }

        club = newUser("club");
        other = newUser("other");
    }

    private Long newUser(String name) {
        return appUserRepository.save(new AppUser("User " + name, name + "@series.test",
                passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    private List<Long> ids(List<TimeSlot> slots) {
        return slots.stream().map(TimeSlot::getId).toList();
    }

    private SlotStatus statusOf(TimeSlot slot) {
        return timeSlotRepository.findById(slot.getId()).orElseThrow().getStatus();
    }

    @Test
    void previewShowsWhichWeeksAreFree() {
        bookingService.bookSlot(halfA.get(1).getId(), other); // covers the full hall in week 2
        bookingService.blockSlot(full.get(2).getId());        // week 3 closed

        List<SeriesWeek> weeks = bookingService.previewSeries(full.get(0).getId(), 4);

        assertEquals(List.of(SeriesWeek.Status.FREE, SeriesWeek.Status.TAKEN,
                        SeriesWeek.Status.CLOSED, SeriesWeek.Status.NO_SLOT),
                weeks.stream().map(SeriesWeek::status).toList());
        assertNull(weeks.get(3).slot());
        assertEquals(full.get(0).getStartTime().plusWeeks(3), weeks.get(3).startTime());
    }

    @Test
    void bookingASeriesBooksEveryWeekUnderOneSeries() {
        List<Booking> bookings = bookingService.bookSeries(ids(full), club);

        assertEquals(3, bookings.size());
        Long seriesId = bookings.get(0).getSeries().getId();
        assertTrue(bookings.stream().allMatch(b -> b.getSeries().getId().equals(seriesId)));
        full.forEach(s -> assertEquals(SlotStatus.BOOKED, statusOf(s)));
        halfA.subList(0, 3).forEach(s -> assertEquals(SlotStatus.UNAVAILABLE, statusOf(s)));
        assertEquals(SlotStatus.AVAILABLE, statusOf(halfA.get(3)), "week 4 has no full-hall booking");
    }

    @Test
    void ifAnyWeekIsTakenNothingIsBooked() {
        bookingService.bookSlot(halfA.get(2).getId(), other); // week 3 of the full hall is now covered

        SlotNotAvailableException e = assertThrows(SlotNotAvailableException.class,
                () -> bookingService.bookSeries(ids(full), club));

        assertTrue(e.getMessage().contains("Nothing was booked"));
        assertEquals(SlotStatus.AVAILABLE, statusOf(full.get(0)));
        assertEquals(SlotStatus.AVAILABLE, statusOf(full.get(1)));
        assertTrue(bookingService.getBookingsForUser(club).isEmpty());
    }

    @Test
    void canBookJustTheFreeWeeks() {
        bookingService.bookSlot(halfA.get(1).getId(), other);

        List<Booking> bookings = bookingService.bookSeries(
                List.of(full.get(0).getId(), full.get(2).getId()), club);

        assertEquals(2, bookings.size());
    }

    @Test
    void seriesHasToBeTheSameTimeEachWeek() {
        TimeSlot later = timeSlotRepository.save(new TimeSlot(halfACourt,
                halfA.get(1).getStartTime().plusHours(1), halfA.get(1).getEndTime().plusHours(1)));

        assertThrows(BadRequestException.class,
                () -> bookingService.bookSeries(List.of(halfA.get(0).getId(), later.getId()), club));
    }

    @Test
    void seriesHasToBeOnOneCourt() {
        assertThrows(BadRequestException.class,
                () -> bookingService.bookSeries(List.of(halfA.get(0).getId(), halfB.get(1).getId()), club));
    }

    @Test
    void seriesLengthIsCapped() {
        assertThrows(BadRequestException.class,
                () -> bookingService.previewSeries(full.get(0).getId(), BookingService.MAX_SERIES_WEEKS + 1));
    }

    @Test
    void cancellingASeriesFreesEveryWeekAndServesTheWaitlist() {
        Long seriesId = bookingService.bookSeries(ids(full), club).get(0).getSeries().getId();
        // Half A is covered by the full-hall booking in week 2, so "other" can queue for it
        bookingService.joinWaitlist(halfA.get(1).getId(), other);

        int cancelled = bookingService.cancelSeries(seriesId, club);

        assertEquals(3, cancelled);
        assertTrue(bookingService.getBookingsForUser(club).isEmpty());
        assertEquals(other,
                bookingRepository.findByTimeSlotId(halfA.get(1).getId()).orElseThrow().getBookedBy().getId());
        assertEquals(SlotStatus.AVAILABLE, statusOf(full.get(0)));
    }

    @Test
    void cannotCancelSomeoneElsesSeries() {
        Long seriesId = bookingService.bookSeries(ids(full), club).get(0).getSeries().getId();

        assertThrows(SlotNotAvailableException.class, () -> bookingService.cancelSeries(seriesId, other));
        assertEquals(3, bookingService.getBookingsForUser(club).size());
    }

    @Test
    void cancellingOneWeekLeavesTheRestOfTheSeries() {
        List<Booking> bookings = bookingService.bookSeries(ids(full), club);

        bookingService.cancelBooking(bookings.get(1).getId(), club);

        assertEquals(2, bookingService.getBookingsForUser(club).size());
        assertEquals(SlotStatus.AVAILABLE, statusOf(full.get(1)));
    }
}
