package com.courtbooking.config;

import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds demo data so the app is usable straight after startup.
 * The venues and facilities follow what the University of Leicester lists
 * publicly. The time slots are demo slots, not the real timetable.
 */
@Component
public class DevDataSeeder implements CommandLineRunner {

    // Each sports hall is one floor marked for several sports, so the HALL is
    // the bookable space, not one "court" per sport. Otherwise two people could
    // book the same floor at the same time under different sports.
    private static final String HALL_SPORTS = "Volleyball, Badminton, Basketball, Netball, Futsal";

    // Most recent figure found (an older university page). Check it is still right.
    private static final int TENNIS_COURT_COUNT = 11;

    private final OrganisationRepository organisationRepository;
    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(OrganisationRepository organisationRepository, VenueRepository venueRepository,
                         CourtRepository courtRepository, TimeSlotRepository timeSlotRepository,
                         AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.organisationRepository = organisationRepository;
        this.venueRepository = venueRepository;
        this.courtRepository = courtRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (organisationRepository.count() > 0) {
            return; // already seeded
        }

        Organisation uni = new Organisation(
                "University of Leicester Sports Centre",
                "On-campus sports facilities open to students and the public.",
                "sports@leicester-demo.ac.uk");
        organisationRepository.save(uni);

        Venue danielleBrown = venueRepository.save(new Venue(
                "Danielle Brown Sports Centre",
                "Wyggeston Drive (Gate 1), University Road, Leicester LE1 7RH", uni));
        Venue rogerBettles = venueRepository.save(new Venue(
                "Roger Bettles Sports Centre",
                "40 Manor Road, Oadby, Leicester LE2 2LL", uni));
        Venue charlesWilson = venueRepository.save(new Venue(
                "Charles Wilson Building",
                "Charles Wilson Building, University of Leicester campus, Leicester", uni));

        // Danielle Brown: the hall can be booked whole, or as two halves.
        // The halves point at the full hall as their parent, so booking one
        // blocks the other (see BookingService).
        Court dbHall = saveCourtWithSlots("Sports Hall (Full)", HALL_SPORTS, danielleBrown, null);
        saveCourtWithSlots("Sports Hall - Half A", HALL_SPORTS, danielleBrown, dbHall);
        saveCourtWithSlots("Sports Hall - Half B", HALL_SPORTS, danielleBrown, dbHall);

        // Roger Bettles: hall is booked whole, the astro can be booked whole or
        // as halves, plus the 5-a-side cage and the tennis courts.
        saveCourtWithSlots("Sports Hall", HALL_SPORTS, rogerBettles, null);
        Court astro = saveCourtWithSlots("All-Weather Astro Pitch (Full)", "Football", rogerBettles, null);
        saveCourtWithSlots("Astro Pitch - Half A", "Football", rogerBettles, astro);
        saveCourtWithSlots("Astro Pitch - Half B", "Football", rogerBettles, astro);
        saveCourtWithSlots("5-a-Side Cage", "Football", rogerBettles, null);
        for (int i = 1; i <= TENNIS_COURT_COUNT; i++) {
            saveCourtWithSlots("Tennis Court " + i, "Tennis", rogerBettles, null);
        }

        // Charles Wilson Building: badminton is the only sport confirmed there.
        saveCourtWithSlots("Sports Hall", "Badminton", charlesWilson, null);

        // Demo accounts: admin/password123 and student/password123
        AppUser admin = new AppUser("Venue Admin", "admin@leicester-demo.ac.uk",
                passwordEncoder.encode("password123"), Role.ORG_ADMIN);
        admin.setOrganisation(uni);
        appUserRepository.save(admin);

        AppUser student = new AppUser("Demo Student", "student@leicester-demo.ac.uk",
                passwordEncoder.encode("password123"), Role.PUBLIC_USER);
        appUserRepository.save(student);
    }

    // Saves one bookable space plus a week of hourly demo slots (9am-9pm).
    // A full space and its halves get identical slot times, which is what
    // lets BookingService match them up.
    private Court saveCourtWithSlots(String name, String sportType, Venue venue, Court parent) {
        Court court = courtRepository.save(new Court(name, sportType, null, venue, parent));

        List<TimeSlot> slots = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int day = 0; day < 7; day++) {
            LocalDateTime slotStart = today.plusDays(day).atTime(LocalTime.of(9, 0));
            LocalDateTime dayEnd = today.plusDays(day).atTime(LocalTime.of(21, 0));
            while (!slotStart.plusHours(1).isAfter(dayEnd)) {
                slots.add(new TimeSlot(court, slotStart, slotStart.plusHours(1)));
                slotStart = slotStart.plusHours(1);
            }
        }
        timeSlotRepository.saveAll(slots);
        return court;
    }
}