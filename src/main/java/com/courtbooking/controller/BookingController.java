package com.courtbooking.controller;

import com.courtbooking.dto.BookingDtos.BookingRequest;
import com.courtbooking.dto.BookingDtos.BookingResponse;
import com.courtbooking.model.Booking;
import com.courtbooking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/me")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal(expression = "id") Long userId) {
        return bookingService.getBookingsForUser(userId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<BookingResponse> bookSlot(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal(expression = "id") Long userId) {
        Booking booking = bookingService.bookSlot(request.timeSlotId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booking));
    }

    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable Long bookingId,
            @AuthenticationPrincipal(expression = "id") Long userId) {
        bookingService.cancelBooking(bookingId, userId);
        return ResponseEntity.noContent().build();
    }
}
