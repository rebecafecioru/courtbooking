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
 * An admin blocking the full hall at the same moment a user books one of its
 * halves. Exactly one must win: either the half is booked and the block is
 * refused, or the hall is blocked and the booking is refused. Never a booking
 * on a closed hall. Run many times on fresh slots so both orders happen.
 */
@SpringBootTest
class SlotBlockingConcurrencyTest {

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
    private Long userId;

    // Not @Transactional (the threads need to see each other's commits), so
    // nothing is rolled back: a random email keeps runs from clashing.
    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Race Uni", "d", "r@example.com"));
        Venue venue = venueRepository.save(new Venue("Race Venue", "addr", org));
        full = courtRepository.save(new Court("Hall (Full)", "Volleyball", 12, venue));
        halfA = courtRepository.save(new Court("Hall - Half A", "Volleyball", 6, venue, full));
        courtRepository.save(new Court("Hall - Half B", "Volleyball", 6, venue, full));
        userId = appUserRepository.save(new AppUser("User", UUID.randomUUID() + "@race.test",
                passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    @Test
    void blockingTheHallAndBookingAHalfAtOnceNeverBothSucceed() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                LocalDateTime start = LocalDateTime.now().plusDays(1).plusHours(round);
                Long fullSlotId = timeSlotRepository.save(new TimeSlot(full, start, start.plusHours(1))).getId();
                Long halfSlotId = timeSlotRepository.save(new TimeSlot(halfA, start, start.plusHours(1))).getId();

                CountDownLatch go = new CountDownLatch(1);
                Future<Boolean> block = executor.submit(() -> {
                    go.await();
                    try {
                        bookingService.blockSlot(fullSlotId);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                Future<Boolean> booking = executor.submit(() -> {
                    go.await();
                    try {
                        bookingService.bookSlot(halfSlotId, userId);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                go.countDown();

                boolean blocked = block.get(5, TimeUnit.SECONDS);
                boolean booked = booking.get(5, TimeUnit.SECONDS);

                assertTrue(blocked != booked, "round " + round + ": exactly one should win, got blocked="
                        + blocked + ", booked=" + booked);
                SlotStatus fullStatus = timeSlotRepository.findById(fullSlotId).orElseThrow().getStatus();
                SlotStatus halfStatus = timeSlotRepository.findById(halfSlotId).orElseThrow().getStatus();
                if (blocked) {
                    assertEquals(SlotStatus.BLOCKED, fullStatus, "round " + round);
                    assertEquals(SlotStatus.UNAVAILABLE, halfStatus, "round " + round);
                } else {
                    assertEquals(SlotStatus.UNAVAILABLE, fullStatus, "round " + round);
                    assertEquals(SlotStatus.BOOKED, halfStatus, "round " + round);
                }
            }
        } finally {
            executor.shutdown();
        }
    }
}
