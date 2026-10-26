package com.courtbooking.service;

import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A club booking the full hall for 4 weeks at the same moment someone books
 * Half A in week 3. Exactly one can win, and the series must never end up
 * half-booked: either all 4 weeks are the club's and the single booking was
 * refused, or the single booking went through and the club got nothing.
 * Run many times, each on a fresh hall, so both orders happen.
 */
@SpringBootTest
class SeriesBookingConcurrencyTest {

    private static final int ROUNDS = 20;
    private static final int WEEKS = 4;

    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Venue venue;
    private Long club;
    private Long player;

    // Not @Transactional (the threads need to see each other's commits), so
    // nothing is rolled back: random emails keep runs from clashing.
    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Race Uni", "d", "r@example.com"));
        venue = venueRepository.save(new Venue("Race Venue", "addr", org));
        club = newUser();
        player = newUser();
    }

    private Long newUser() {
        return appUserRepository.save(new AppUser("User", UUID.randomUUID() + "@race.test",
                passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    @Test
    void aSeriesAndAClashingSingleBookingNeverBothSucceed() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Court full = courtRepository.save(new Court("Hall " + round, "Volleyball", 12, venue));
                Court halfA = courtRepository.save(new Court("Hall " + round + " A", "Volleyball", 6, venue, full));
                LocalDateTime start = LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0);

                List<Long> fullWeeks = new ArrayList<>();
                Long halfAWeek3 = null;
                for (int week = 0; week < WEEKS; week++) {
                    LocalDateTime s = start.plusWeeks(week);
                    fullWeeks.add(timeSlotRepository.save(new TimeSlot(full, s, s.plusHours(1))).getId());
                    Long half = timeSlotRepository.save(new TimeSlot(halfA, s, s.plusHours(1))).getId();
                    if (week == 2) {
                        halfAWeek3 = half;
                    }
                }
                Long clashingSlot = halfAWeek3;

                CountDownLatch go = new CountDownLatch(1);
                Future<Boolean> series = executor.submit(() -> {
                    go.await();
                    try {
                        bookingService.bookSeries(fullWeeks, club);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                Future<Boolean> single = executor.submit(() -> {
                    go.await();
                    try {
                        bookingService.bookSlot(clashingSlot, player);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                go.countDown();

                boolean seriesBooked = series.get(10, TimeUnit.SECONDS);
                boolean singleBooked = single.get(10, TimeUnit.SECONDS);

                assertTrue(seriesBooked != singleBooked, "round " + round + ": exactly one should win, got series="
                        + seriesBooked + ", single=" + singleBooked);
                long clubWeeks = fullWeeks.stream()
                        .filter(id -> bookingRepository.findByTimeSlotId(id).isPresent())
                        .count();
                assertEquals(seriesBooked ? WEEKS : 0, clubWeeks,
                        "round " + round + ": the series must be all or nothing");
            }
        } finally {
            executor.shutdown();
        }
    }
}
