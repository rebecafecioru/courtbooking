package com.courtbooking.service;

import com.courtbooking.dto.CreateSlotsRequest;
import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Races between generating slots and changing slots in the same hall. Each
 * race is run many times on fresh days so the threads get a chance to
 * interleave both ways:
 * <p>
 * 1. Generating Half A's slots while someone books the full hall over part of
 *    that time: whichever goes first, the Half A slot under the booking must
 *    end up UNAVAILABLE, not AVAILABLE.
 * 2. Two admins generating the same slots for a court at once: no duplicates.
 */
@SpringBootTest
class SlotGenerationConcurrencyTest {

    private static final int ROUNDS = 20;

    @Autowired private BookingService bookingService;
    @Autowired private TimeSlotService timeSlotService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Court full;
    private Court halfA;
    private Long user;

    // Not @Transactional (the threads need to see each other's commits), so
    // nothing is rolled back: random emails keep runs from clashing.
    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Gen Race Uni", "d", "genrace@example.com"));
        Venue venue = venueRepository.save(new Venue("Gen Race Venue", "addr", org));
        full = courtRepository.save(new Court("Hall (Full)", "Volleyball", 12, venue));
        halfA = courtRepository.save(new Court("Hall - Half A", "Volleyball", 6, venue, full));
        user = appUserRepository.save(new AppUser("User", UUID.randomUUID() + "@genrace.test",
                passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    // A fresh day for each round
    private LocalDate dayOf(int round) {
        return LocalDate.now().plusDays(3 + round);
    }

    private CreateSlotsRequest nineToTwelve(Court court, LocalDate day, int minutes) {
        return new CreateSlotsRequest(court.getId(), day, day, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(12, 0), minutes);
    }

    private List<TimeSlot> slotsOn(Court court, LocalDate day) {
        return timeSlotRepository.findByCourtIdAndStartTimeBetween(
                court.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay()).stream()
                .sorted(Comparator.comparing(TimeSlot::getStartTime))
                .toList();
    }

    @Test
    void halfSlotsGeneratedWhileTheFullHallIsBookedEndUpUnavailable() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                LocalDate day = dayOf(round);
                Long hallSlot = timeSlotRepository.save(
                        new TimeSlot(full, day.atTime(9, 0), day.atTime(10, 0))).getId();

                CountDownLatch go = new CountDownLatch(1);
                Future<?> generate = executor.submit(() -> {
                    go.await();
                    timeSlotService.generateSlots(nineToTwelve(halfA, day, 90));
                    return null;
                });
                Future<?> book = executor.submit(() -> {
                    go.await();
                    bookingService.bookSlot(hallSlot, user);
                    return null;
                });
                go.countDown(); // release both threads at the same instant

                generate.get(10, TimeUnit.SECONDS);
                book.get(10, TimeUnit.SECONDS); // new half slots never stop the hall being booked

                // 09:00-10:30 is under the booked hour, 10:30-12:00 isn't
                assertEquals(List.of(SlotStatus.UNAVAILABLE, SlotStatus.AVAILABLE),
                        slotsOn(halfA, day).stream().map(TimeSlot::getStatus).toList(), "round " + round);
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void twoGenerationsAtOnceDoNotCreateDuplicates() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                LocalDate day = dayOf(round);

                CountDownLatch go = new CountDownLatch(1);
                Callable<Integer> generate = () -> {
                    go.await();
                    return timeSlotService.generateSlots(nineToTwelve(full, day, 60)).size();
                };
                Future<Integer> f1 = executor.submit(generate);
                Future<Integer> f2 = executor.submit(generate);
                go.countDown();

                int created = f1.get(10, TimeUnit.SECONDS) + f2.get(10, TimeUnit.SECONDS);
                assertEquals(3, created, "round " + round);
                assertEquals(3, slotsOn(full, day).size(), "round " + round);
            }
        } finally {
            executor.shutdown();
        }
    }
}
