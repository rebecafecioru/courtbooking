package com.courtbooking.service;

import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Races between slots on the full hall and Half A whose times overlap without
 * matching (full hall 60-minute slots, Half A 90-minute ones). Each race is run
 * many times on fresh slots so the threads get a chance to interleave both ways:
 * <p>
 * 1. Booking the full hall 09:00-10:00 against Half A 09:30-11:00: exactly one
 *    may succeed.
 * 2. Cancelling the full hall 09:00-10:00 (which hands Half A 09:30-11:00 to
 *    someone waiting) against booking the full hall 10:00-11:00: Half A and the
 *    10:00 full hall must never both end up booked.
 */
@SpringBootTest
class MisalignedSlotsConcurrencyTest {

    private static final int ROUNDS = 20;

    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Court full;
    private Court halfA;
    private Long userA;
    private Long userB;
    private Long userC;

    // Not @Transactional (the threads need to see each other's commits), so
    // nothing is rolled back: random emails keep runs from clashing.
    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Race Uni", "d", "race@example.com"));
        Venue venue = venueRepository.save(new Venue("Race Venue", "addr", org));
        full = courtRepository.save(new Court("Hall (Full)", "Volleyball", 12, venue));
        halfA = courtRepository.save(new Court("Hall - Half A", "Volleyball", 6, venue, full));
        courtRepository.save(new Court("Hall - Half B", "Volleyball", 6, venue, full));
        userA = newUser();
        userB = newUser();
        userC = newUser();
    }

    private Long newUser() {
        return appUserRepository.save(new AppUser("User", UUID.randomUUID() + "@race.test",
                passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    // A fresh 09:00 for each round, on its own day
    private LocalDateTime nineOn(int round) {
        return LocalDateTime.now().plusDays(3 + round).withHour(9).withMinute(0).withSecond(0).withNano(0);
    }

    private Long slot(Court court, LocalDateTime start, LocalDateTime end) {
        return timeSlotRepository.save(new TimeSlot(court, start, end)).getId();
    }

    private SlotStatus statusOf(Long slotId) {
        return timeSlotRepository.findById(slotId).orElseThrow().getStatus();
    }

    private Future<Boolean> tryBooking(ExecutorService executor, CountDownLatch go, Long slotId, Long userId) {
        return executor.submit(() -> {
            go.await();
            try {
                bookingService.bookSlot(slotId, userId);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Test
    void fullHallAndAPartlyOverlappingHalfCannotBothBeBooked() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                LocalDateTime nine = nineOn(round);
                Long fullSlot = slot(full, nine, nine.plusHours(1));
                Long halfSlot = slot(halfA, nine.plusMinutes(30), nine.plusHours(2));

                CountDownLatch go = new CountDownLatch(1);
                Future<Boolean> f1 = tryBooking(executor, go, fullSlot, userA);
                Future<Boolean> f2 = tryBooking(executor, go, halfSlot, userB);
                go.countDown(); // release both threads at the same instant

                boolean fullWon = f1.get(10, TimeUnit.SECONDS);
                boolean halfWon = f2.get(10, TimeUnit.SECONDS);
                assertTrue(fullWon ^ halfWon, "round " + round + ": exactly one booking should succeed");
                assertEquals(fullWon ? SlotStatus.UNAVAILABLE : SlotStatus.BOOKED, statusOf(halfSlot));
                assertEquals(fullWon ? SlotStatus.BOOKED : SlotStatus.UNAVAILABLE, statusOf(fullSlot));
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void aPromotionOntoALongHalfSlotNeverClashesWithABookingItNowCovers() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                LocalDateTime nine = nineOn(round);
                Long full9 = slot(full, nine, nine.plusHours(1));
                Long full10 = slot(full, nine.plusHours(1), nine.plusHours(2));
                Long half930 = slot(halfA, nine.plusMinutes(30), nine.plusHours(2));

                Booking hall = bookingService.bookSlot(full9, userA);
                bookingService.joinWaitlist(half930, userB);

                CountDownLatch go = new CountDownLatch(1);
                Future<?> cancel = executor.submit(() -> {
                    go.await();
                    bookingService.cancelBooking(hall.getId(), userA);
                    return null;
                });
                Future<Boolean> booking10 = tryBooking(executor, go, full10, userC);
                go.countDown();

                cancel.get(10, TimeUnit.SECONDS); // the cancel itself must always succeed
                boolean cGotTen = booking10.get(10, TimeUnit.SECONDS);

                SlotStatus half = statusOf(half930);
                SlotStatus ten = statusOf(full10);
                assertFalse(half == SlotStatus.BOOKED && ten == SlotStatus.BOOKED,
                        "round " + round + ": Half A 09:30 and the full hall 10:00 are both booked");
                if (cGotTen) {
                    // C booked 10:00 first, so Half A stays covered and B keeps waiting
                    assertEquals(SlotStatus.BOOKED, ten, "round " + round);
                    assertEquals(SlotStatus.UNAVAILABLE, half, "round " + round);
                } else {
                    // The cancel went first and gave Half A to B, which covers 10:00
                    assertEquals(SlotStatus.BOOKED, half, "round " + round);
                    assertEquals(SlotStatus.UNAVAILABLE, ten, "round " + round);
                }
            }
        } finally {
            executor.shutdown();
        }
    }
}
