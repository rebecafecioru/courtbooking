package com.courtbooking.controller;

import com.courtbooking.config.AppUserPrincipal;
import com.courtbooking.dto.CreateSlotsRequest;
import com.courtbooking.dto.TimeSlotResponse;
import com.courtbooking.model.TimeSlot;
import com.courtbooking.service.BookingService;
import com.courtbooking.service.OrgAccessService;
import com.courtbooking.service.TimeSlotService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;
    private final BookingService bookingService;
    private final OrgAccessService orgAccessService;

    public TimeSlotController(TimeSlotService timeSlotService, BookingService bookingService,
                              OrgAccessService orgAccessService) {
        this.timeSlotService = timeSlotService;
        this.bookingService = bookingService;
        this.orgAccessService = orgAccessService;
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

    // Org admin: bulk-generate slots for one of their own courts
    @PostMapping("/admin/slots/generate")
    public ResponseEntity<List<TimeSlotResponse>> generateSlots(@Valid @RequestBody CreateSlotsRequest request,
                                                                @AuthenticationPrincipal AppUserPrincipal admin) {
        orgAccessService.checkCanManageCourt(admin, request.courtId());
        List<TimeSlot> created = timeSlotService.generateSlots(request);
        return ResponseEntity.ok(created.stream().map(TimeSlotResponse::from).toList());
    }

    // Org admin: block a slot (e.g. maintenance). 409 if it's booked.
    @PatchMapping("/admin/slots/{slotId}/block")
    public ResponseEntity<Void> blockSlot(@PathVariable Long slotId, @AuthenticationPrincipal AppUserPrincipal admin) {
        orgAccessService.checkCanManageSlot(admin, slotId);
        bookingService.blockSlot(slotId);
        return ResponseEntity.noContent().build();
    }

    // Org admin: reopen a blocked slot; anyone waiting for it gets it
    @PatchMapping("/admin/slots/{slotId}/unblock")
    public ResponseEntity<Void> unblockSlot(@PathVariable Long slotId, @AuthenticationPrincipal AppUserPrincipal admin) {
        orgAccessService.checkCanManageSlot(admin, slotId);
        bookingService.unblockSlot(slotId);
        return ResponseEntity.noContent().build();
    }
}
