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

import static org.junit.jupiter.api.Assertions.*;

//each test method's database changes are rolled back automatically once it finishes, so tests don't leak data into each other

@SpringBootTest
@Transactional
class BookingServiceTest {

    @Autowired private BookingService bookingService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long slotId;
    private Long userId;

    @BeforeEach
    void setUp() {
        Organization org = organizationRepository.save(new Organization("Test Uni", "d", "t@example.com"));
        Venue venue = venueRepository.save(new Venue("Venue", "addr", org));
        Court court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
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
}