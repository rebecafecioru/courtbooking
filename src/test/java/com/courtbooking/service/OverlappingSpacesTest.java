package com.courtbooking.service;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the rule that a full space and its halves can never be booked at the
 * same time: booking the full hall covers both halves, and booking a half
 * covers the full hall (but not the other half).
 */
@SpringBootTest
@Transactional
class OverlappingSpacesTest {

    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private TimeSlot fullSlot;
    private TimeSlot halfASlot;
    private TimeSlot halfBSlot;
    private Long userId;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Overlap Uni", "d", "o@example.com"));
        Venue venue = venueRepository.save(new Venue("Overlap Venue", "addr", org));

        Court full = courtRepository.save(new Court("Hall (Full)", "Volleyball", 12, venue));
        Court halfA = courtRepository.save(new Court("Hall - Half A", "Volleyball", 6, venue, full));
        Court halfB = courtRepository.save(new Court("Hall - Half B", "Volleyball", 6, venue, full));

        // Identical times on all three, which is what links them together.
        LocalDateTime start = LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        fullSlot = timeSlotRepository.save(new TimeSlot(full, start, end));
        halfASlot = timeSlotRepository.save(new TimeSlot(halfA, start, end));
        halfBSlot = timeSlotRepository.save(new TimeSlot(halfB, start, end));

        userId = appUserRepository.save(
                new AppUser("Overlap User", "overlap@test.com", passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    private SlotStatus statusOf(TimeSlot slot) {
        return timeSlotRepository.findById(slot.getId()).orElseThrow().getStatus();
    }

    @Test
    void bookingTheFullHallBlocksBothHalves() {
        bookingService.bookSlot(fullSlot.getId(), userId);

        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfASlot));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfBSlot));
        assertThrows(SlotNotAvailableException.class, () -> bookingService.bookSlot(halfASlot.getId(), userId));
        assertThrows(SlotNotAvailableException.class, () -> bookingService.bookSlot(halfBSlot.getId(), userId));
    }

    @Test
    void bookingAHalfBlocksTheFullHallButNotTheOtherHalf() {
        bookingService.bookSlot(halfASlot.getId(), userId);

        assertEquals(SlotStatus.UNAVAILABLE, statusOf(fullSlot));
        assertEquals(SlotStatus.AVAILABLE, statusOf(halfBSlot));
        assertDoesNotThrow(() -> bookingService.bookSlot(halfBSlot.getId(), userId));
        assertThrows(SlotNotAvailableException.class, () -> bookingService.bookSlot(fullSlot.getId(), userId));
    }

    @Test
    void cancellingOneHalfKeepsTheFullHallBlockedWhileTheOtherHalfIsBooked() {
        Booking a = bookingService.bookSlot(halfASlot.getId(), userId);
        bookingService.bookSlot(halfBSlot.getId(), userId);

        bookingService.cancelBooking(a.getId(), userId);

        assertEquals(SlotStatus.AVAILABLE, statusOf(halfASlot));
        assertEquals(SlotStatus.BOOKED, statusOf(halfBSlot));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(fullSlot));
    }

    @Test
    void cancellingTheLastBookingFreesTheFullHall() {
        Booking a = bookingService.bookSlot(halfASlot.getId(), userId);

        bookingService.cancelBooking(a.getId(), userId);

        assertEquals(SlotStatus.AVAILABLE, statusOf(fullSlot));
        assertDoesNotThrow(() -> bookingService.bookSlot(fullSlot.getId(), userId));
    }

    @Test
    void aBlockedHalfStopsTheFullHallBeingBooked() {
        halfASlot.setStatus(SlotStatus.BLOCKED); // e.g. maintenance on one half
        timeSlotRepository.saveAndFlush(halfASlot);

        assertThrows(SlotNotAvailableException.class, () -> bookingService.bookSlot(fullSlot.getId(), userId));
        // the other half is unaffected
        assertDoesNotThrow(() -> bookingService.bookSlot(halfBSlot.getId(), userId));
    }
}