package com.courtbooking.service;

import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is the test that justifies the whole architecture: it fires two
 * booking requests at the exact same slot at the exact same time, from two
 * separate threads/transactions, and asserts that exactly one succeeds.
 * If the locking in BookingService were removed, this test would be flaky
 * and occasionally show 2 successes - i.e. a double-booking bug in production.
 */
@SpringBootTest
class BookingServiceConcurrencyTest {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private OrganisationRepository organisationRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private CourtRepository courtRepository;
    @Autowired
    private TimeSlotRepository timeSlotRepository;
    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long slotId;
    private Long userAId;
    private Long userBId;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(
                new Organisation("Test Uni", "desc", "test@example.com"));
        Venue venue = venueRepository.save(new Venue("Test Venue", "123 Test St", org));
        Court court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
        TimeSlot slot = timeSlotRepository.save(
                new TimeSlot(court, LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1)));
        slotId = slot.getId();

        userAId = appUserRepository.save(
                new AppUser("User A", "a@test.com", passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
        userBId = appUserRepository.save(
                new AppUser("User B", "b@test.com", passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    @Test
    void onlyOneOfTwoSimultaneousBookingsSucceeds() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch goLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> attemptBookingWithUser(userAId, readyLatch, goLatch, successCount, failureCount));
        Future<?> f2 = executor.submit(() -> attemptBookingWithUser(userBId, readyLatch, goLatch, successCount, failureCount));

        readyLatch.await(5, TimeUnit.SECONDS);
        goLatch.countDown(); // release both threads simultaneously

        try {
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one booking attempt should succeed");
        assertEquals(1, failureCount.get(), "Exactly one booking attempt should be rejected as a conflict");
    }

    private void attemptBookingWithUser(Long userId, CountDownLatch readyLatch, CountDownLatch goLatch,
                                         AtomicInteger successCount, AtomicInteger failureCount) {
        try {
            readyLatch.countDown();
            goLatch.await();
            bookingService.bookSlot(slotId, userId);
            successCount.incrementAndGet();
        } catch (Exception e) {
            failureCount.incrementAndGet();
        }
    }
}
