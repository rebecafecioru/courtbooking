package com.courtbooking.service;

import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.exception.WaitlistException;
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
class WaitlistTest {

    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // A full hall split into two halves, all with a slot at the same time
    private TimeSlot fullSlot;
    private TimeSlot halfASlot;
    private TimeSlot halfBSlot;
    private Long userA;
    private Long userB;
    private Long userC;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Waitlist Uni", "d", "w@example.com"));
        Venue venue = venueRepository.save(new Venue("Waitlist Venue", "addr", org));
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
        userC = newUser("c");
    }

    private Long newUser(String name) {
        return appUserRepository.save(new AppUser("User " + name, name + "@waitlist.test",
                passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    private Long bookedBy(TimeSlot slot) {
        return bookingRepository.findByTimeSlotId(slot.getId()).orElseThrow().getBookedBy().getId();
    }

    private SlotStatus statusOf(TimeSlot slot) {
        return timeSlotRepository.findById(slot.getId()).orElseThrow().getStatus();
    }

    @Test
    void joiningABookedSlotGivesPlacesInOrder() {
        bookingService.bookSlot(halfASlot.getId(), userA);

        WaitlistEntry b = bookingService.joinWaitlist(halfASlot.getId(), userB);
        WaitlistEntry c = bookingService.joinWaitlist(halfASlot.getId(), userC);

        assertEquals(1, bookingService.positionOf(b));
        assertEquals(2, bookingService.positionOf(c));
    }

    @Test
    void cannotJoinTheWaitlistForAFreeSlot() {
        assertThrows(WaitlistException.class, () -> bookingService.joinWaitlist(halfASlot.getId(), userB));
    }

    @Test
    void cannotJoinTheWaitlistForAClosedSlot() {
        halfASlot.setStatus(SlotStatus.BLOCKED);

        assertThrows(WaitlistException.class, () -> bookingService.joinWaitlist(halfASlot.getId(), userB));
    }

    @Test
    void cannotJoinTheSameWaitlistTwice() {
        bookingService.bookSlot(halfASlot.getId(), userA);
        bookingService.joinWaitlist(halfASlot.getId(), userB);

        assertThrows(WaitlistException.class, () -> bookingService.joinWaitlist(halfASlot.getId(), userB));
    }

    @Test
    void cannotJoinTheWaitlistForYourOwnBooking() {
        bookingService.bookSlot(halfASlot.getId(), userA);

        assertThrows(WaitlistException.class, () -> bookingService.joinWaitlist(halfASlot.getId(), userA));
    }

    @Test
    void cancellingGivesTheSlotToTheFirstInLine() {
        Booking booking = bookingService.bookSlot(halfASlot.getId(), userA);
        bookingService.joinWaitlist(halfASlot.getId(), userB);
        WaitlistEntry c = bookingService.joinWaitlist(halfASlot.getId(), userC);

        bookingService.cancelBooking(booking.getId(), userA);

        assertEquals(SlotStatus.BOOKED, statusOf(halfASlot));
        assertEquals(userB, bookedBy(halfASlot));
        assertTrue(bookingService.getWaitlistForUser(userB).isEmpty(), "B's place became a booking");
        assertEquals(1, bookingService.positionOf(c), "C moves up to next in line");
    }

    @Test
    void leavingTheWaitlistRemovesYourPlace() {
        bookingService.bookSlot(halfASlot.getId(), userA);
        WaitlistEntry b = bookingService.joinWaitlist(halfASlot.getId(), userB);

        bookingService.leaveWaitlist(b.getId(), userB);

        assertFalse(waitlistEntryRepository.existsById(b.getId()));
    }

    @Test
    void cannotLeaveSomeoneElsesWaitlistPlace() {
        bookingService.bookSlot(halfASlot.getId(), userA);
        WaitlistEntry b = bookingService.joinWaitlist(halfASlot.getId(), userB);

        assertThrows(ResourceNotFoundException.class, () -> bookingService.leaveWaitlist(b.getId(), userC));
    }

    @Test
    void cancellingAHalfGivesTheFullHallToSomeoneWaitingForIt() {
        Booking booking = bookingService.bookSlot(halfASlot.getId(), userA);
        // The full hall is UNAVAILABLE while Half A is booked, so B can queue for it
        bookingService.joinWaitlist(fullSlot.getId(), userB);

        bookingService.cancelBooking(booking.getId(), userA);

        assertEquals(userB, bookedBy(fullSlot));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfASlot));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfBSlot));
    }

    @Test
    void fullHallIsNotHandedOutWhileTheOtherHalfIsClosed() {
        Booking booking = bookingService.bookSlot(halfASlot.getId(), userA);
        WaitlistEntry b = bookingService.joinWaitlist(fullSlot.getId(), userB);
        halfBSlot.setStatus(SlotStatus.BLOCKED); // e.g. maintenance on Half B

        bookingService.cancelBooking(booking.getId(), userA);

        assertTrue(bookingRepository.findByTimeSlotId(fullSlot.getId()).isEmpty());
        assertTrue(waitlistEntryRepository.existsById(b.getId()), "B keeps their place in line");
    }

    @Test
    void earlierInLineWinsWhenAFullHallCancelFreesBothHalves() {
        Booking booking = bookingService.bookSlot(fullSlot.getId(), userA);
        bookingService.joinWaitlist(halfASlot.getId(), userB); // first in line
        bookingService.joinWaitlist(halfBSlot.getId(), userC); // doesn't clash with B

        bookingService.cancelBooking(booking.getId(), userA);

        assertEquals(userB, bookedBy(halfASlot));
        assertEquals(userC, bookedBy(halfBSlot));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(fullSlot));
    }
}
