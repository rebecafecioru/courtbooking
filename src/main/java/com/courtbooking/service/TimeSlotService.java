package com.courtbooking.service;

import com.courtbooking.dto.CreateSlotsRequest;
import com.courtbooking.exception.ResourceNotFoundException;
import com.courtbooking.model.Court;
import com.courtbooking.model.SlotStatus;
import com.courtbooking.model.TimeSlot;
import com.courtbooking.repository.CourtRepository;
import com.courtbooking.repository.TimeSlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final CourtRepository courtRepository;

    public TimeSlotService(TimeSlotRepository timeSlotRepository, CourtRepository courtRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.courtRepository = courtRepository;
    }

    public List<TimeSlot> getAvailableSlots(Long courtId, LocalDateTime from, LocalDateTime to) {
        return timeSlotRepository.findByCourtIdAndStatusAndStartTimeBetween(
                courtId, SlotStatus.AVAILABLE, from, to);
    }

    public List<TimeSlot> getAllSlots(Long courtId, LocalDateTime from, LocalDateTime to) {
        return timeSlotRepository.findByCourtIdAndStartTimeBetween(courtId, from, to);
    }

    /**
     * Bulk-generates slots for a court over a date range, on the given weekdays,
     * chopped into fixed-duration chunks. Used by org admins instead of them
     * having to create every single slot by hand.
     * <p>
     * Any new slot that would overlap a slot the court already has is skipped.
     * Without this, running the same generation twice would create two slots
     * for the same court at the same time, and two people could each book one:
     * a double booking the locking in BookingService can't see, because it
     * locks per slot.
     * <p>
     * A new slot starts UNAVAILABLE instead of AVAILABLE if a slot on an
     * overlapping space (the full hall, or one of its halves) whose time
     * overlaps it is BOOKED or BLOCKED, the same rule BookingService applies.
     * <p>
     * Before reading anything, this locks the hall's top-level court row, the
     * same lock BookingService takes before its slot locks. So a booking,
     * cancel or block in the hall finishes before we look, and one that starts
     * after waits until our slots are committed, then sees and refreshes them.
     * Two generations for the same hall queue up the same way, so the second
     * sees the first's slots and skips them. The related slots are then
     * locked too, in id order like BookingService.
     */
    @Transactional
    public List<TimeSlot> generateSlots(CreateSlotsRequest req) {
        Court court = courtRepository.findById(req.courtId())
                .orElseThrow(() -> new ResourceNotFoundException("Court " + req.courtId() + " not found"));
        Long hallId = court.getParent() != null ? court.getParent().getId() : court.getId();
        courtRepository.findByIdForUpdate(hallId);

        // Everything the court already has around this date range, to check against
        List<TimeSlot> existing = timeSlotRepository.findByCourtIdAndStartTimeBetween(court.getId(),
                req.fromDate().minusDays(1).atStartOfDay(), req.toDate().plusDays(1).atStartOfDay());

        // The booked or closed slots on overlapping spaces in the date range, read under lock
        List<TimeSlot> taken = lockRelatedSlots(court,
                req.fromDate().atStartOfDay(), req.toDate().plusDays(1).atStartOfDay()).stream()
                .filter(t -> t.getStatus() == SlotStatus.BOOKED || t.getStatus() == SlotStatus.BLOCKED)
                .toList();

        List<TimeSlot> created = new ArrayList<>();
        var date = req.fromDate();
        while (!date.isAfter(req.toDate())) {
            if (req.daysOfWeek().contains(date.getDayOfWeek())) {
                var slotStart = date.atTime(req.dailyStartTime());
                var dayEnd = date.atTime(req.dailyEndTime());
                while (slotStart.plusMinutes(req.slotDurationMinutes()).compareTo(dayEnd) <= 0) {
                    var slotEnd = slotStart.plusMinutes(req.slotDurationMinutes());
                    if (!overlapsAny(existing, slotStart, slotEnd)) {
                        TimeSlot slot = new TimeSlot(court, slotStart, slotEnd);
                        if (overlapsAny(taken, slotStart, slotEnd)) {
                            slot.setStatus(SlotStatus.UNAVAILABLE);
                        }
                        created.add(slot);
                    }
                    slotStart = slotEnd;
                }
            }
            date = date.plusDays(1);
        }
        return timeSlotRepository.saveAll(created);
    }

    // Locks the slots on the courts sharing space with this one (its parent and
    // its children) that overlap the time window.
    private List<TimeSlot> lockRelatedSlots(Court court, LocalDateTime start, LocalDateTime end) {
        List<Long> relatedCourtIds = new ArrayList<>();
        if (court.getParent() != null) {
            relatedCourtIds.add(court.getParent().getId());
        }
        courtRepository.findByParentId(court.getId()).forEach(c -> relatedCourtIds.add(c.getId()));
        if (relatedCourtIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = timeSlotRepository.findIdsByCourtsOverlapping(relatedCourtIds, start, end);
        return ids.isEmpty() ? List.of() : timeSlotRepository.findAllByIdForUpdate(ids);
    }

    // Two time ranges overlap if each starts before the other ends.
    private boolean overlapsAny(List<TimeSlot> slots, LocalDateTime start, LocalDateTime end) {
        return slots.stream().anyMatch(s -> s.getStartTime().isBefore(end) && s.getEndTime().isAfter(start));
    }

    // Blocking and unblocking live in BookingService: they need the same locks
    // as booking and cancelling, and unblocking can hand a slot to the waitlist.
}
