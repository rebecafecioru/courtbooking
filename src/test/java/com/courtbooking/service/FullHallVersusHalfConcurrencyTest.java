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
 * One person tries to book the FULL hall while another tries to book HALF A,
 * at the same instant, for the same time. Both bookings touch the same
 * physical floor, so exactly one of them must succeed.
 */
@SpringBootTest
class FullHallVersusHalfConcurrencyTest {

    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long fullSlotId;
    private Long halfASlotId;
    private Long userCId;
    private Long userDId;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Race Uni", "d", "race@example.com"));
        Venue venue = venueRepository.save(new Venue("Race Venue", "addr", org));

        Court full = courtRepository.save(new Court("Hall (Full)", "Volleyball", 12, venue));
        Court halfA = courtRepository.save(new Court("Hall - Half A", "Volleyball", 6, venue, full));
        Court halfB = courtRepository.save(new Court("Hall - Half B", "Volleyball", 6, venue, full));

        LocalDateTime start = LocalDateTime.now().plusDays(2).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        fullSlotId = timeSlotRepository.save(new TimeSlot(full, start, end)).getId();
        halfASlotId = timeSlotRepository.save(new TimeSlot(halfA, start, end)).getId();
        timeSlotRepository.save(new TimeSlot(halfB, start, end));

        userCId = appUserRepository.save(
                new AppUser("User C", "c@test.com", passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
        userDId = appUserRepository.save(
                new AppUser("User D", "d@test.com", passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    @Test
    void fullHallAndHalfCannotBothBeBookedAtTheSameTime() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch goLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() ->
                attempt(fullSlotId, userCId, readyLatch, goLatch, successCount, failureCount));
        Future<?> f2 = executor.submit(() ->
                attempt(halfASlotId, userDId, readyLatch, goLatch, successCount, failureCount));

        readyLatch.await(5, TimeUnit.SECONDS);
        goLatch.countDown(); // release both threads at the same instant

        try {
            f1.get(10, TimeUnit.SECONDS);
            f2.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one of the two overlapping bookings should succeed");
        assertEquals(1, failureCount.get(), "The other should be rejected as a conflict");
    }

    private void attempt(Long slotId, Long userId, CountDownLatch readyLatch, CountDownLatch goLatch,
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
