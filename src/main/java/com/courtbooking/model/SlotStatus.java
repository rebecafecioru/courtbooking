package com.courtbooking.model;

public enum SlotStatus {
    AVAILABLE,
    BOOKED,
    BLOCKED,     // set by an org admin, e.g. maintenance
    UNAVAILABLE  // free itself, but covered by a booking on a related space
    // (e.g. the full hall while one of its halves is booked)
}