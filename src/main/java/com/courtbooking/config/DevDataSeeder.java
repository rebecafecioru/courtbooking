package com.courtbooking.config;

import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Seeds a couple of organizations, venues, courts and slots so the app is
 * demoable immediately after `./gradlew bootRun`, with no manual setup.
 */
@Component
public class DevDataSeeder implements CommandLineRunner {

    private final OrganizationRepository organizationRepository;
    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(OrganizationRepository organizationRepository, VenueRepository venueRepository,
                          CourtRepository courtRepository, TimeSlotRepository timeSlotRepository,
                          AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.organizationRepository = organizationRepository;
        this.venueRepository = venueRepository;
        this.courtRepository = courtRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (organizationRepository.count() > 0) {
            return; // already seeded
        }

        Organization uni = new Organization(
                "University of Leicester Sports Centre",
                "On-campus sports facilities open to students and the public.",
                "sports@leicester-demo.ac.uk");
        organizationRepository.save(uni);

        Venue mainVenue = new Venue("Danielle Brown Sports Centre", "University Rd, Leicester", uni);
        venueRepository.save(mainVenue);

        Court volleyballCourt = new Court("Court 1", "Volleyball", 12, mainVenue);
        Court badmintonCourt = new Court("Court 2", "Badminton", 4, mainVenue);
        courtRepository.save(volleyballCourt);
        courtRepository.save(badmintonCourt);

        // Generate a week of hourly slots, 9am-9pm, for both courts
        LocalDate today = LocalDate.now();
        for (Court court : new Court[]{volleyballCourt, badmintonCourt}) {
            for (int day = 0; day < 7; day++) {
                LocalDate date = today.plusDays(day);
                LocalDateTime slotStart = date.atTime(LocalTime.of(9, 0));
                LocalDateTime dayEnd = date.atTime(LocalTime.of(21, 0));
                while (slotStart.plusHours(1).compareTo(dayEnd) <= 0) {
                    timeSlotRepository.save(new TimeSlot(court, slotStart, slotStart.plusHours(1)));
                    slotStart = slotStart.plusHours(1);
                }
            }
        }

        // Demo accounts: admin/password123 and student/password123
        AppUser admin = new AppUser("Venue Admin", "admin@leicester-demo.ac.uk",
                passwordEncoder.encode("password123"), Role.ORG_ADMIN);
        admin.setOrganization(uni);
        appUserRepository.save(admin);

        AppUser student = new AppUser("Demo Student", "student@leicester-demo.ac.uk",
                passwordEncoder.encode("password123"), Role.PUBLIC_USER);
        appUserRepository.save(student);
    }
}
