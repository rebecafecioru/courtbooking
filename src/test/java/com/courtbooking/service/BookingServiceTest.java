package com.courtbooking.service;

import com.courtbooking.exception.ResourceNotFoundException;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

//each test method's database changes are rolled back automatically once it finishes, so tests don't leak data into each other

@SpringBootTest
@Transactional
class BookingServiceTest {

    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Court court;
    private Long slotId;
    private Long userId;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Test Uni", "d", "t@example.com"));
        Venue venue = venueRepository.save(new Venue("Venue", "addr", org));
        court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
        TimeSlot slot = timeSlotRepository.save(new TimeSlot(court,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1)));
        slotId = slot.getId();
        userId = appUserRepository.save(
                new AppUser("User", "u@test.com", passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    @Test
    void bookingAnAvailableSlotSucceeds() {
        Booking booking = bookingService.bookSlot(slotId, userId);

        assertNotNull(booking.getId());
        assertEquals(Booking.BookingStatus.CONFIRMED, booking.getStatus());
        assertEquals(SlotStatus.BOOKED, timeSlotRepository.findById(slotId).orElseThrow().getStatus());
    }

    @Test
    void bookingAnAlreadyBookedSlotThrows() {
        bookingService.bookSlot(slotId, userId);

        assertThrows(SlotNotAvailableException.class, () -> bookingService.bookSlot(slotId, userId));
    }

    @Test
    void bookingANonexistentSlotThrows() {
        assertThrows(ResourceNotFoundException.class, () -> bookingService.bookSlot(999_999L, userId));
    }

    @Test
    void cancellingABookingFreesTheSlotAgain() {
        Booking booking = bookingService.bookSlot(slotId, userId);

        bookingService.cancelBooking(booking.getId(), userId);

        assertEquals(SlotStatus.AVAILABLE, timeSlotRepository.findById(slotId).orElseThrow().getStatus());
        // and it can be booked again
        assertDoesNotThrow(() -> bookingService.bookSlot(slotId, userId));
    }

    @Test
    void cancellingSomeoneElsesBookingThrows() {
        Booking booking = bookingService.bookSlot(slotId, userId);
        Long otherUserId = appUserRepository.save(
                new AppUser("Other", "other@test.com", passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();

        assertThrows(SlotNotAvailableException.class,
                () -> bookingService.cancelBooking(booking.getId(), otherUserId));
    }

    @Test
    void getBookingsForUserReturnsOnlyTheirBookingsEarliestFirst() {
        Long laterSlotId = timeSlotRepository.save(new TimeSlot(court,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(1))).getId();
        Long otherSlotId = timeSlotRepository.save(new TimeSlot(court,
                LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(3).plusHours(1))).getId();
        Long otherUserId = appUserRepository.save(
                new AppUser("Other", "other@test.com", passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();

        // booked out of order, to check the sorting
        bookingService.bookSlot(laterSlotId, userId);
        bookingService.bookSlot(slotId, userId);
        bookingService.bookSlot(otherSlotId, otherUserId);

        List<Booking> bookings = bookingService.getBookingsForUser(userId);

        assertEquals(List.of(slotId, laterSlotId),
                bookings.stream().map(b -> b.getTimeSlot().getId()).toList());
    }

    @Test
    void getBookingsForUserIsEmptyAfterCancelling() {
        Booking booking = bookingService.bookSlot(slotId, userId);
        bookingService.cancelBooking(booking.getId(), userId);

        assertTrue(bookingService.getBookingsForUser(userId).isEmpty());
    }
}
