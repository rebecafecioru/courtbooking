package com.courtbooking.controller;

import com.courtbooking.dto.BookingDtos.BookingRequest;
import com.courtbooking.dto.BookingDtos.BookingResponse;
import com.courtbooking.dto.SeriesDtos.SeriesBookingRequest;
import com.courtbooking.dto.SeriesDtos.SeriesBookingResponse;
import com.courtbooking.dto.SeriesDtos.SeriesCancelResponse;
import com.courtbooking.dto.SeriesDtos.SeriesWeekResponse;
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

    // Which weeks of "this slot, every week" are free. Books nothing.
    @GetMapping("/series/preview")
    public List<SeriesWeekResponse> previewSeries(@RequestParam Long firstSlotId, @RequestParam int weeks) {
        return bookingService.previewSeries(firstSlotId, weeks).stream()
                .map(SeriesWeekResponse::from)
                .toList();
    }

    // Books the given slots as one weekly series. All or nothing: 409 and
    // nothing booked if any of them isn't free.
    @PostMapping("/series")
    public ResponseEntity<SeriesBookingResponse> bookSeries(
            @Valid @RequestBody SeriesBookingRequest request,
            @AuthenticationPrincipal(expression = "id") Long userId) {
        List<Booking> bookings = bookingService.bookSeries(request.timeSlotIds(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new SeriesBookingResponse(
                bookings.get(0).getSeries().getId(),
                bookings.stream().map(BookingResponse::from).toList()));
    }

    // Cancels every week of the series that hasn't started yet
    @DeleteMapping("/series/{seriesId}")
    public SeriesCancelResponse cancelSeries(
            @PathVariable Long seriesId,
            @AuthenticationPrincipal(expression = "id") Long userId) {
        return new SeriesCancelResponse(bookingService.cancelSeries(seriesId, userId));
    }
}
