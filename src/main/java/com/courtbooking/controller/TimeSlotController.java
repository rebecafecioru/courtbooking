package com.courtbooking.controller;

import com.courtbooking.dto.CreateSlotsRequest;
import com.courtbooking.dto.TimeSlotResponse;
import com.courtbooking.model.TimeSlot;
import com.courtbooking.service.TimeSlotService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    public TimeSlotController(TimeSlotService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    // Public: browse available slots for a court within a date range
    @GetMapping("/courts/{courtId}/slots")
    public List<TimeSlotResponse> getSlots(
            @PathVariable Long courtId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "true") boolean availableOnly) {
        List<TimeSlot> slots = availableOnly
                ? timeSlotService.getAvailableSlots(courtId, from, to)
                : timeSlotService.getAllSlots(courtId, from, to);
        return slots.stream().map(TimeSlotResponse::from).toList();
    }

    // Org admin: bulk-generate slots for a court
    @PostMapping("/admin/slots/generate")
    public ResponseEntity<List<TimeSlotResponse>> generateSlots(@Valid @RequestBody CreateSlotsRequest request) {
        List<TimeSlot> created = timeSlotService.generateSlots(request);
        return ResponseEntity.ok(created.stream().map(TimeSlotResponse::from).toList());
    }

    // Org admin: block a slot (e.g. maintenance)
    @PatchMapping("/admin/slots/{slotId}/block")
    public ResponseEntity<Void> blockSlot(@PathVariable Long slotId) {
        timeSlotService.blockSlot(slotId);
        return ResponseEntity.noContent().build();
    }
}
