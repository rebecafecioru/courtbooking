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

@SpringBootTest
@Transactional
class SlotBlockingTest {

    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // A full hall split into two halves, all with a slot at the same time
    private TimeSlot fullSlot;
    private TimeSlot halfASlot;
    private TimeSlot halfBSlot;
    private Long userA;
    private Long userB;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Block Uni", "d", "b@example.com"));
        Venue venue = venueRepository.save(new Venue("Block Venue", "addr", org));
        Court full = courtRepository.save(new Court("Hall (Full)", "Volleyball", 12, venue));
        Court halfA = courtRepository.save(new Court("Hall - Half A", "Volleyball", 6, venue, full));
        Court halfB = courtRepository.save(new Court("Hall - Half B", "Volleyball", 6, venue, full));

        LocalDateTime start = LocalDateTime.now().plusDays(1).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        fullSlot = timeSlotRepository.save(new TimeSlot(full, start, end));
        halfASlot = timeSlotRepository.save(new TimeSlot(halfA, start, end));
        halfBSlot = timeSlotRepository.save(new TimeSlot(halfB, start, end));

        userA = newUser("a");
        userB = newUser("b");
    }

    private Long newUser(String name) {
        return appUserRepository.save(new AppUser("User " + name, name + "@block.test",
                passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    private SlotStatus statusOf(TimeSlot slot) {
        return timeSlotRepository.findById(slot.getId()).orElseThrow().getStatus();
    }

    @Test
    void blockingAFreeSlotClosesIt() {
        bookingService.blockSlot(halfASlot.getId());

        assertEquals(SlotStatus.BLOCKED, statusOf(halfASlot));
        assertThrows(SlotNotAvailableException.class, () -> bookingService.bookSlot(halfASlot.getId(), userA));
    }

    @Test
    void cannotBlockABookedSlot() {
        bookingService.bookSlot(halfASlot.getId(), userA);

        assertThrows(SlotNotAvailableException.class, () -> bookingService.blockSlot(halfASlot.getId()));
        assertEquals(SlotStatus.BOOKED, statusOf(halfASlot));
        assertTrue(bookingRepository.findByTimeSlotId(halfASlot.getId()).isPresent(), "the booking is untouched");
    }

    @Test
    void cannotBlockTheFullHallWhileAHalfIsBooked() {
        bookingService.bookSlot(halfASlot.getId(), userA);

        assertThrows(SlotNotAvailableException.class, () -> bookingService.blockSlot(fullSlot.getId()));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(fullSlot));
    }

    @Test
    void cannotBlockAHalfWhileTheFullHallIsBooked() {
        bookingService.bookSlot(fullSlot.getId(), userA);

        assertThrows(SlotNotAvailableException.class, () -> bookingService.blockSlot(halfASlot.getId()));
    }

    @Test
    void blockingAHalfMakesTheFullHallUnavailableButNotTheOtherHalf() {
        bookingService.blockSlot(halfASlot.getId());

        assertEquals(SlotStatus.UNAVAILABLE, statusOf(fullSlot));
        assertEquals(SlotStatus.AVAILABLE, statusOf(halfBSlot));
    }

    @Test
    void blockingTheFullHallMakesBothHalvesUnavailable() {
        bookingService.blockSlot(fullSlot.getId());

        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfASlot));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfBSlot));
    }

    @Test
    void unblockingFreesTheSlotAndTheFullHallAgain() {
        bookingService.blockSlot(halfASlot.getId());

        bookingService.unblockSlot(halfASlot.getId());

        assertEquals(SlotStatus.AVAILABLE, statusOf(halfASlot));
        assertEquals(SlotStatus.AVAILABLE, statusOf(fullSlot));
        assertDoesNotThrow(() -> bookingService.bookSlot(fullSlot.getId(), userA));
    }

    @Test
    void unblockingKeepsTheFullHallUnavailableWhileTheOtherHalfIsBooked() {
        bookingService.blockSlot(halfASlot.getId());
        bookingService.bookSlot(halfBSlot.getId(), userA);

        bookingService.unblockSlot(halfASlot.getId());

        assertEquals(SlotStatus.AVAILABLE, statusOf(halfASlot));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(fullSlot));
    }

    @Test
    void unblockingGivesTheSlotToSomeoneWaiting() {
        bookingService.blockSlot(halfBSlot.getId());
        // The full hall is UNAVAILABLE while Half B is closed, so B can queue for it
        bookingService.joinWaitlist(fullSlot.getId(), userB);

        bookingService.unblockSlot(halfBSlot.getId());

        assertEquals(userB,
                bookingRepository.findByTimeSlotId(fullSlot.getId()).orElseThrow().getBookedBy().getId());
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfBSlot));
    }

    @Test
    void blockingAndUnblockingTwiceIsHarmless() {
        bookingService.blockSlot(halfASlot.getId());
        assertDoesNotThrow(() -> bookingService.blockSlot(halfASlot.getId()));

        bookingService.unblockSlot(halfASlot.getId());
        assertDoesNotThrow(() -> bookingService.unblockSlot(halfASlot.getId()));
        assertEquals(SlotStatus.AVAILABLE, statusOf(halfASlot));
    }
}
