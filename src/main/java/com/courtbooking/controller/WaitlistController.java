package com.courtbooking.controller;

import com.courtbooking.dto.WaitlistDtos.WaitlistRequest;
import com.courtbooking.dto.WaitlistDtos.WaitlistResponse;
import com.courtbooking.model.WaitlistEntry;
import com.courtbooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

    private final BookingService bookingService;

    public WaitlistController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<WaitlistResponse> join(
            @Valid @RequestBody WaitlistRequest request,
            @AuthenticationPrincipal(expression = "id") Long userId) {
        WaitlistEntry entry = bookingService.joinWaitlist(request.timeSlotId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WaitlistResponse.from(entry, bookingService.positionOf(entry)));
    }

    @GetMapping("/me")
    public List<WaitlistResponse> myWaitlist(@AuthenticationPrincipal(expression = "id") Long userId) {
        return bookingService.getWaitlistForUser(userId).stream()
                .map(entry -> WaitlistResponse.from(entry, bookingService.positionOf(entry)))
                .toList();
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> leave(
            @PathVariable Long entryId,
            @AuthenticationPrincipal(expression = "id") Long userId) {
        bookingService.leaveWaitlist(entryId, userId);
        return ResponseEntity.noContent().build();
    }
}
