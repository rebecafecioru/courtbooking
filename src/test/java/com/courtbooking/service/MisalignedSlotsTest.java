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
 * The full hall and its halves with different timetables: the hall in
 * 60-minute slots, Half A in 90-minute ones. Slots clash whenever their times
 * overlap, not only when they match exactly.
 * <pre>
 *   Full:   09:00-10:00 | 10:00-11:00 | 11:00-12:00
 *   Half A:      09:30--------11:00 | 11:00--------12:30
 *   Half B: 09:00-10:00
 * </pre>
 */
@SpringBootTest
@Transactional
class MisalignedSlotsTest {

    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private WaitlistEntryRepository waitlistEntryRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private TimeSlot full9;
    private TimeSlot full10;
    private TimeSlot full11;
    private TimeSlot halfA930;
    private TimeSlot halfA11;
    private TimeSlot halfB9;
    private Long userA;
    private Long userB;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Misaligned Uni", "d", "m@example.com"));
        Venue venue = venueRepository.save(new Venue("Misaligned Venue", "addr", org));
        Court full = courtRepository.save(new Court("Hall (Full)", "Volleyball", 12, venue));
        Court halfA = courtRepository.save(new Court("Hall - Half A", "Volleyball", 6, venue, full));
        Court halfB = courtRepository.save(new Court("Hall - Half B", "Volleyball", 6, venue, full));

        LocalDateTime nine = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        full9 = timeSlotRepository.save(new TimeSlot(full, nine, nine.plusHours(1)));
        full10 = timeSlotRepository.save(new TimeSlot(full, nine.plusHours(1), nine.plusHours(2)));
        full11 = timeSlotRepository.save(new TimeSlot(full, nine.plusHours(2), nine.plusHours(3)));
        halfA930 = timeSlotRepository.save(new TimeSlot(halfA, nine.plusMinutes(30), nine.plusHours(2)));
        halfA11 = timeSlotRepository.save(new TimeSlot(halfA, nine.plusHours(2), nine.plusMinutes(210)));
        halfB9 = timeSlotRepository.save(new TimeSlot(halfB, nine, nine.plusHours(1)));

        userA = newUser("a");
        userB = newUser("b");
    }

    private Long newUser(String name) {
        return appUserRepository.save(new AppUser("User " + name, name + "@misaligned.test",
                passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    private SlotStatus statusOf(TimeSlot slot) {
        return timeSlotRepository.findById(slot.getId()).orElseThrow().getStatus();
    }

    private Long bookedBy(TimeSlot slot) {
        return bookingRepository.findByTimeSlotId(slot.getId()).orElseThrow().getBookedBy().getId();
    }

    @Test
    void bookingTheFullHallCoversAHalfSlotThatOnlyPartlyOverlapsIt() {
        bookingService.bookSlot(full9.getId(), userA);

        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfA930));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfB9));
        assertEquals(SlotStatus.AVAILABLE, statusOf(halfA11));
        assertThrows(SlotNotAvailableException.class, () -> bookingService.bookSlot(halfA930.getId(), userB));
    }

    @Test
    void bookingALongHalfSlotCoversEveryFullHallSlotItTouches() {
        bookingService.bookSlot(halfA930.getId(), userA);

        assertEquals(SlotStatus.UNAVAILABLE, statusOf(full9));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(full10));
        assertEquals(SlotStatus.AVAILABLE, statusOf(full11)); // starts as Half A's slot ends
        assertEquals(SlotStatus.AVAILABLE, statusOf(halfB9)); // the other half is unaffected
        assertThrows(SlotNotAvailableException.class, () -> bookingService.bookSlot(full9.getId(), userB));
        assertThrows(SlotNotAvailableException.class, () -> bookingService.bookSlot(full10.getId(), userB));
        assertDoesNotThrow(() -> bookingService.bookSlot(halfB9.getId(), userB));
    }

    @Test
    void backToBackSlotsDoNotClash() {
        bookingService.bookSlot(halfA930.getId(), userA); // ends 11:00

        assertDoesNotThrow(() -> bookingService.bookSlot(full11.getId(), userB)); // starts 11:00
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfA11));
    }

    @Test
    void cancellingTheFullHallPromotesAWaiterOnAnOverlappingHalfSlot() {
        Booking hall = bookingService.bookSlot(full9.getId(), userA);
        bookingService.joinWaitlist(halfA930.getId(), userB); // UNAVAILABLE, so B can queue

        bookingService.cancelBooking(hall.getId(), userA);

        assertEquals(SlotStatus.BOOKED, statusOf(halfA930));
        assertEquals(userB, bookedBy(halfA930));
        // B's 09:30-11:00 now covers both full-hall slots, including the
        // 10:00 one that the cancelled 09:00 booking never touched.
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(full9));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(full10));
        assertEquals(SlotStatus.AVAILABLE, statusOf(halfB9));
        assertEquals(SlotStatus.AVAILABLE, statusOf(full11));
    }

    @Test
    void aHalfSlotStaysCoveredWhileAnyFullHallSlotOverlappingItIsBooked() {
        Booking first = bookingService.bookSlot(full9.getId(), userA);
        Booking second = bookingService.bookSlot(full10.getId(), userA);
        bookingService.joinWaitlist(halfA930.getId(), userB);

        bookingService.cancelBooking(first.getId(), userA);

        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfA930)); // 10:00-11:00 is still booked
        assertTrue(waitlistEntryRepository.existsByTimeSlotIdAndUserId(halfA930.getId(), userB));
        assertEquals(SlotStatus.AVAILABLE, statusOf(full9));

        bookingService.cancelBooking(second.getId(), userA);

        assertEquals(userB, bookedBy(halfA930));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(full9));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(full10));
    }

    @Test
    void blockingAHalfSlotCoversEveryFullHallSlotItTouches() {
        bookingService.blockSlot(halfA930.getId());

        assertEquals(SlotStatus.UNAVAILABLE, statusOf(full9));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(full10));
        assertEquals(SlotStatus.AVAILABLE, statusOf(full11));
        assertThrows(SlotNotAvailableException.class, () -> bookingService.bookSlot(full10.getId(), userA));
    }

    @Test
    void cannotBlockAHalfSlotWhileAPartlyOverlappingFullHallSlotIsBooked() {
        bookingService.bookSlot(full10.getId(), userA);

        assertThrows(SlotNotAvailableException.class, () -> bookingService.blockSlot(halfA930.getId()));
        assertDoesNotThrow(() -> bookingService.blockSlot(halfA11.getId())); // back-to-back with 10:00-11:00
    }

    @Test
    void unblockingAHalfSlotPromotesAWaiterOnAnOverlappingFullHallSlot() {
        bookingService.blockSlot(halfA930.getId());
        bookingService.joinWaitlist(full10.getId(), userB); // UNAVAILABLE while the half is closed

        bookingService.unblockSlot(halfA930.getId());

        assertEquals(userB, bookedBy(full10));
        assertEquals(SlotStatus.UNAVAILABLE, statusOf(halfA930)); // now covered by B's booking
        assertEquals(SlotStatus.AVAILABLE, statusOf(full9));
    }
}
