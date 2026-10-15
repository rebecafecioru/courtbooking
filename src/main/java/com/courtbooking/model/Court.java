package com.courtbooking.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name; // e.g. "Sports Hall - Half A"

    @NotBlank
    private String sportType; // e.g. "Volleyball, Basketball"

    private Integer capacity; // optional, e.g. max players

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    // If this court is one half of a bigger space (e.g. Half A of the full
    // sports hall), parent points at the full space. It is null for ordinary
    // courts and for the full space itself.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_court_id")
    private Court parent;

    @OneToMany(mappedBy = "court", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TimeSlot> timeSlots = new ArrayList<>();

    protected Court() {
    }

    public Court(String name, String sportType, Integer capacity, Venue venue) {
        this.name = name;
        this.sportType = sportType;
        this.capacity = capacity;
        this.venue = venue;
    }

    public Court(String name, String sportType, Integer capacity, Venue venue, Court parent) {
        this(name, sportType, capacity, venue);
        this.parent = parent;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSportType() {
        return sportType;
    }

    public void setSportType(String sportType) {
        this.sportType = sportType;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Venue getVenue() {
        return venue;
    }

    public void setVenue(Venue venue) {
        this.venue = venue;
    }

    public Court getParent() {
        return parent;
    }

    public void setParent(Court parent) {
        this.parent = parent;
    }

    public List<TimeSlot> getTimeSlots() {
        return timeSlots;
    }

    public void setTimeSlots(List<TimeSlot> timeSlots) {
        this.timeSlots = timeSlots;
    }
}