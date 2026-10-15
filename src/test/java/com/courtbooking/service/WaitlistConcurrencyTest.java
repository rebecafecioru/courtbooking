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
 * The waitlist's two races, each run many times on fresh slots so the threads
 * get a chance to interleave both ways round:
 * <p>
 * 1. A cancel against a direct booking. The person waiting must get the slot,
 *    not someone who happened to click Book at the same moment.
 * 2. Joining the waitlist against a cancel. The user must either be given the
 *    slot or be told it's free. They must never be left queueing for a free slot.
 */
@SpringBootTest
class WaitlistConcurrencyTest {

    private static final int ROUNDS = 20;

    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Court court;
    private Long userA;
    private Long userB;
    private Long userC;

    // Not @Transactional (the threads need to see each other's commits), so
    // nothing is rolled back: random emails keep runs from clashing.
    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Race Uni", "d", "r@example.com"));
        Venue venue = venueRepository.save(new Venue("Race Venue", "addr", org));
        court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
        userA = newUser();
        userB = newUser();
        userC = newUser();
    }

    private Long newUser() {
        return appUserRepository.save(new AppUser("User", UUID.randomUUID() + "@race.test",
                passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    private Long newSlot(int round) {
        LocalDateTime start = LocalDateTime.now().plusDays(1).plusHours(round);
        return timeSlotRepository.save(new TimeSlot(court, start, start.plusHours(1))).getId();
    }

    @Test
    void personWaitingGetsTheSlotNotSomeoneBookingAtTheSameMoment() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Long slotId = newSlot(round);
                Booking booking = bookingService.bookSlot(slotId, userA);
                bookingService.joinWaitlist(slotId, userB);

                CountDownLatch go = new CountDownLatch(1);
                Future<?> cancel = executor.submit(() -> {
                    go.await();
                    bookingService.cancelBooking(booking.getId(), userA);
                    return null;
                });
                Future<Boolean> directBooking = executor.submit(() -> {
                    go.await();
                    try {
                        bookingService.bookSlot(slotId, userC);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                go.countDown();

                cancel.get(5, TimeUnit.SECONDS); // the cancel itself must always succeed
                assertFalse(directBooking.get(5, TimeUnit.SECONDS),
                        "round " + round + ": C jumped the queue");
                assertEquals(userB,
                        bookingRepository.findByTimeSlotId(slotId).orElseThrow().getBookedBy().getId(),
                        "round " + round + ": the slot should have gone to B");
            }
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void joiningDuringACancelNeverLeavesSomeoneWaitingForAFreeSlot() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Long slotId = newSlot(round);
                Booking booking = bookingService.bookSlot(slotId, userA);

                CountDownLatch go = new CountDownLatch(1);
                Future<?> cancel = executor.submit(() -> {
                    go.await();
                    bookingService.cancelBooking(booking.getId(), userA);
                    return null;
                });
                Future<Boolean> join = executor.submit(() -> {
                    go.await();
                    try {
                        bookingService.joinWaitlist(slotId, userB);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                });
                go.countDown();

                cancel.get(5, TimeUnit.SECONDS);
                boolean joined = join.get(5, TimeUnit.SECONDS);

                SlotStatus status = timeSlotRepository.findById(slotId).orElseThrow().getStatus();
                boolean stillWaiting = waitlistEntryRepository.existsByTimeSlotIdAndUserId(slotId, userB);
                assertFalse(stillWaiting, "round " + round + ": B was left on the waitlist");
                if (joined) {
                    // Joined before the cancel, so the cancel handed B the slot
                    assertEquals(SlotStatus.BOOKED, status, "round " + round);
                    assertEquals(userB,
                            bookingRepository.findByTimeSlotId(slotId).orElseThrow().getBookedBy().getId());
                } else {
                    // The cancel went first, so B was told the slot is free
                    assertEquals(SlotStatus.AVAILABLE, status, "round " + round);
                }
            }
        } finally {
            executor.shutdown();
        }
    }
}
