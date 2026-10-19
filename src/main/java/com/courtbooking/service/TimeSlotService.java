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
     * locks per slot. (Two admins generating for the same court at the same
     * instant could still race; that's rare enough to leave for now.)
     */
    @Transactional
    public List<TimeSlot> generateSlots(CreateSlotsRequest req) {
        Court court = courtRepository.findById(req.courtId())
                .orElseThrow(() -> new ResourceNotFoundException("Court " + req.courtId() + " not found"));

        // Everything the court already has around this date range, to check against
        List<TimeSlot> existing = timeSlotRepository.findByCourtIdAndStartTimeBetween(court.getId(),
                req.fromDate().minusDays(1).atStartOfDay(), req.toDate().plusDays(1).atStartOfDay());

        List<TimeSlot> created = new ArrayList<>();
        var date = req.fromDate();
        while (!date.isAfter(req.toDate())) {
            if (req.daysOfWeek().contains(date.getDayOfWeek())) {
                var slotStart = date.atTime(req.dailyStartTime());
                var dayEnd = date.atTime(req.dailyEndTime());
                while (slotStart.plusMinutes(req.slotDurationMinutes()).compareTo(dayEnd) <= 0) {
                    var slotEnd = slotStart.plusMinutes(req.slotDurationMinutes());
                    if (!overlapsAny(existing, slotStart, slotEnd)) {
                        created.add(new TimeSlot(court, slotStart, slotEnd));
                    }
                    slotStart = slotEnd;
                }
            }
            date = date.plusDays(1);
        }
        return timeSlotRepository.saveAll(created);
    }

    // Two time ranges overlap if each starts before the other ends.
    private boolean overlapsAny(List<TimeSlot> slots, LocalDateTime start, LocalDateTime end) {
        return slots.stream().anyMatch(s -> s.getStartTime().isBefore(end) && s.getEndTime().isAfter(start));
    }

    // Blocking and unblocking live in BookingService: they need the same locks
    // as booking and cancelling, and unblocking can hand a slot to the waitlist.
}
