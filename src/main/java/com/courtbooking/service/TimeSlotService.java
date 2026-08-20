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
     */
    @Transactional
    public List<TimeSlot> generateSlots(CreateSlotsRequest req) {
        Court court = courtRepository.findById(req.courtId())
                .orElseThrow(() -> new ResourceNotFoundException("Court " + req.courtId() + " not found"));

        List<TimeSlot> created = new ArrayList<>();
        var date = req.fromDate();
        while (!date.isAfter(req.toDate())) {
            if (req.daysOfWeek().contains(date.getDayOfWeek())) {
                var slotStart = date.atTime(req.dailyStartTime());
                var dayEnd = date.atTime(req.dailyEndTime());
                while (slotStart.plusMinutes(req.slotDurationMinutes()).compareTo(dayEnd) <= 0) {
                    var slotEnd = slotStart.plusMinutes(req.slotDurationMinutes());
                    created.add(new TimeSlot(court, slotStart, slotEnd));
                    slotStart = slotEnd;
                }
            }
            date = date.plusDays(1);
        }
        return timeSlotRepository.saveAll(created);
    }

    @Transactional
    public void blockSlot(Long slotId) {
        TimeSlot slot = timeSlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot " + slotId + " not found"));
        slot.setStatus(SlotStatus.BLOCKED);
        timeSlotRepository.save(slot);
    }
}
