package com.courtbooking.service;

import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.exception.SlotNotAvailableException;
import com.courtbooking.model.AppUser;
import com.courtbooking.model.Booking;
import com.courtbooking.model.SlotStatus;
import com.courtbooking.model.TimeSlot;
import com.courtbooking.repository.AppUserRepository;
import com.courtbooking.repository.BookingRepository;
import com.courtbooking.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final TimeSlotRepository timeSlotRepository;
    private final BookingRepository bookingRepository;
    private final AppUserRepository appUserRepository;

    public BookingService(TimeSlotRepository timeSlotRepository,
                          BookingRepository bookingRepository,
                          AppUserRepository appUserRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.bookingRepository = bookingRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public Booking bookSlot(Long timeSlotId, Long userId) {
        TimeSlot slot = timeSlotRepository.findByIdForUpdate(timeSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot " + timeSlotId + " not found"));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new SlotNotAvailableException(
                    "This slot is no longer available (status: " + slot.getStatus() + ").");
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));

        slot.setStatus(SlotStatus.BOOKED);
        timeSlotRepository.save(slot); // optimistic @Version check happens on commit

        Booking booking = new Booking(slot, user);
        return bookingRepository.save(booking); // unique constraint is the final backstop
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long requestingUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));

        if (!booking.getBookedBy().getId().equals(requestingUserId)) {
            throw new SlotNotAvailableException("You can only cancel your own bookings.");
        }

        TimeSlot slot = booking.getTimeSlot();

        bookingRepository.delete(booking);
        bookingRepository.flush();

        slot.setStatus(SlotStatus.AVAILABLE);
        timeSlotRepository.save(slot);
    }
}