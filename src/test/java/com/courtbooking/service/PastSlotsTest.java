package com.courtbooking.service;

import com.courtbooking.exception.SlotNotAvailableException;
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

/**
 * A slot that has already started (here: one that began 30 minutes ago and
 * runs for another 30) can't be booked, cancelled, waitlisted or handed out
 * from its waitlist.
 */
@SpringBootTest
@Transactional
class PastSlotsTest {

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
    private AppUser userA;
    private AppUser userB;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Past Uni", "d", "p@example.com"));
        Venue venue = venueRepository.save(new Venue("Past Venue", "addr", org));
        court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
        userA = newUser("a");
        userB = newUser("b");
    }

    private AppUser newUser(String name) {
        return appUserRepository.save(new AppUser("User " + name, name + "@past.test",
                passwordEncoder.encode("pw"), Role.PUBLIC_USER));
    }

    private TimeSlot startedSlot(SlotStatus status) {
        LocalDateTime now = LocalDateTime.now();
        TimeSlot slot = new TimeSlot(court, now.minusMinutes(30), now.plusMinutes(30));
        slot.setStatus(status);
        return timeSlotRepository.save(slot);
    }

    @Test
    void aSlotThatHasStartedCannotBeBooked() {
        TimeSlot slot = startedSlot(SlotStatus.AVAILABLE);

        assertThrows(SlotNotAvailableException.class, () -> bookingService.bookSlot(slot.getId(), userA.getId()));
        assertTrue(bookingRepository.findByTimeSlotId(slot.getId()).isEmpty());
    }

    @Test
    void aBookingThatHasStartedCannotBeCancelled() {
        TimeSlot slot = startedSlot(SlotStatus.BOOKED);
        Booking booking = bookingRepository.save(new Booking(slot, userA));

        assertThrows(SlotNotAvailableException.class,
                () -> bookingService.cancelBooking(booking.getId(), userA.getId()));
        assertTrue(bookingRepository.findById(booking.getId()).isPresent(), "the booking is untouched");
        assertEquals(SlotStatus.BOOKED, timeSlotRepository.findById(slot.getId()).orElseThrow().getStatus());
    }

    @Test
    void cannotJoinTheWaitlistForASlotThatHasStarted() {
        TimeSlot slot = startedSlot(SlotStatus.BOOKED);
        bookingRepository.save(new Booking(slot, userA));

        assertThrows(WaitlistException.class, () -> bookingService.joinWaitlist(slot.getId(), userB.getId()));
    }

    @Test
    void reopeningAStartedSlotDoesNotHandItToTheWaitlist() {
        TimeSlot slot = startedSlot(SlotStatus.BLOCKED);
        waitlistEntryRepository.save(new WaitlistEntry(slot, userB));

        bookingService.unblockSlot(slot.getId());

        assertEquals(SlotStatus.AVAILABLE, timeSlotRepository.findById(slot.getId()).orElseThrow().getStatus());
        assertTrue(bookingRepository.findByTimeSlotId(slot.getId()).isEmpty(), "nobody is booked into it");
    }
}
