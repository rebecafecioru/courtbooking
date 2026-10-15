package com.courtbooking.service;

import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.exception.SlotNotAvailableException;
import com.courtbooking.model.AppUser;
import com.courtbooking.model.Booking;
import com.courtbooking.model.Court;
import com.courtbooking.model.SlotStatus;
import com.courtbooking.model.TimeSlot;
import com.courtbooking.repository.AppUserRepository;
import com.courtbooking.repository.BookingRepository;
import com.courtbooking.repository.CourtRepository;
import com.courtbooking.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles booking and cancellation.
 * <p>
 * The point of this service is that two people can never end up with the same
 * physical space at the same time. It defends against that in layers:
 * <p>
 * 1. Pessimistic locks - every slot that could clash (the slot itself plus the
 *    same time on any overlapping space) is locked together, in id order, so a
 *    concurrent request has to wait for the first one to finish.
 * 2. Optimistic version check (@Version on TimeSlot) - a backstop if a slot is
 *    ever changed outside this locked path.
 * 3. A unique constraint on booking.time_slot_id - the database itself refuses
 *    two bookings for the same slot.
 * <p>
 * Overlapping spaces: a court can have a parent (Half A and Half B point at the
 * full hall). Booking the full hall marks both halves UNAVAILABLE for that
 * time, and booking a half marks the full hall UNAVAILABLE. Cancelling frees
 * them again, unless another booking still covers them.
 */
@Service
public class BookingService {

    private final TimeSlotRepository timeSlotRepository;
    private final BookingRepository bookingRepository;
    private final AppUserRepository appUserRepository;
    private final CourtRepository courtRepository;

    public BookingService(TimeSlotRepository timeSlotRepository,
                          BookingRepository bookingRepository,
                          AppUserRepository appUserRepository,
                          CourtRepository courtRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.bookingRepository = bookingRepository;
        this.appUserRepository = appUserRepository;
        this.courtRepository = courtRepository;
    }

    @Transactional
    public Booking bookSlot(Long timeSlotId, Long userId) {
        List<TimeSlot> group = lockSlotGroup(timeSlotId);
        TimeSlot slot = findInGroup(group, timeSlotId);

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new SlotNotAvailableException(
                    "This slot is no longer available (status: " + slot.getStatus() + ").");
        }

        // Another slot in the group that is booked, or closed by an admin
        // (e.g. maintenance on one half), also rules this slot out.
        boolean overlapTaken = group.stream()
                .anyMatch(t -> !t.getId().equals(timeSlotId)
                        && (t.getStatus() == SlotStatus.BOOKED || t.getStatus() == SlotStatus.BLOCKED));
        if (overlapTaken) {
            throw new SlotNotAvailableException("Part of this space is already booked or closed at that time.");
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));

        slot.setStatus(SlotStatus.BOOKED);
        timeSlotRepository.save(slot); // optimistic @Version check happens on commit
        refreshOverlaps(group);

        return bookingRepository.save(new Booking(slot, user)); // unique constraint is the final backstop
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long requestingUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking " + bookingId + " not found"));

        if (!booking.getBookedBy().getId().equals(requestingUserId)) {
            throw new SlotNotAvailableException("You can only cancel your own bookings.");
        }

        Long slotId = booking.getTimeSlot().getId();
        List<TimeSlot> group = lockSlotGroup(slotId);
        TimeSlot slot = findInGroup(group, slotId);

        // Deleted rather than soft-marked CANCELLED, because the unique
        // constraint allows only one booking row per slot. flush() makes the
        // DELETE run now, before any later INSERT in the same transaction.
        bookingRepository.delete(booking);
        bookingRepository.flush();

        slot.setStatus(SlotStatus.AVAILABLE);
        refreshOverlaps(group);
    }

    // The user's bookings, earliest first.
    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(Long userId) {
        return bookingRepository.findByUserWithDetails(userId);
    }

    // Locks the slot plus the same time slot on every overlapping court.
    private List<TimeSlot> lockSlotGroup(Long timeSlotId) {
        TimeSlotRepository.SlotWindow window = timeSlotRepository.findWindowById(timeSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot " + timeSlotId + " not found"));

        Set<Long> ids = new HashSet<>();
        ids.add(timeSlotId);

        List<Long> relatedCourtIds = relatedCourtIds(window.getCourtId());
        if (!relatedCourtIds.isEmpty()) {
            ids.addAll(timeSlotRepository.findIdsByCourtsAndTime(
                    relatedCourtIds, window.getStartTime(), window.getEndTime()));
        }
        return timeSlotRepository.findAllByIdForUpdate(ids);
    }

    // Re-works out UNAVAILABLE for every slot in the group. A slot is
    // UNAVAILABLE if an overlapping slot at the same time is BOOKED.
    // BOOKED and admin-BLOCKED slots are left alone.
    private void refreshOverlaps(List<TimeSlot> group) {
        for (TimeSlot t : group) {
            if (t.getStatus() == SlotStatus.BOOKED || t.getStatus() == SlotStatus.BLOCKED) {
                continue;
            }
            List<Long> related = relatedCourtIds(t.getCourt().getId());
            boolean covered = !related.isEmpty()
                    && timeSlotRepository.existsByCourtIdInAndStartTimeAndEndTimeAndStatus(
                    related, t.getStartTime(), t.getEndTime(), SlotStatus.BOOKED);
            t.setStatus(covered ? SlotStatus.UNAVAILABLE : SlotStatus.AVAILABLE);
        }
    }

    // The courts that share physical space with this one: its parent (the full
    // hall, if this is a half) and its children (the halves, if this is the full hall).
    private List<Long> relatedCourtIds(Long courtId) {
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new ResourceNotFoundException("Court " + courtId + " not found"));

        List<Long> ids = new ArrayList<>();
        if (court.getParent() != null) {
            ids.add(court.getParent().getId());
        }
        courtRepository.findByParentId(courtId).forEach(c -> ids.add(c.getId()));
        return ids;
    }

    private TimeSlot findInGroup(List<TimeSlot> group, Long id) {
        return group.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Time slot " + id + " not found"));
    }
}