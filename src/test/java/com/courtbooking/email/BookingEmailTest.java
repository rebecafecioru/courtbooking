package com.courtbooking.email;

import com.courtbooking.exception.SlotNotAvailableException;
import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import com.courtbooking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Emails go out only after the booking's transaction commits, on a background
 * thread. So unlike most tests here this one isn't @Transactional (nothing
 * would ever commit), and it waits for the emails to arrive.
 */
// Its own in-memory database: these tests commit, and a separate context
// (create-drop) mustn't touch the database the other test classes share
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:booking-email-test;DB_CLOSE_DELAY=-1")
class BookingEmailTest {

    // Records what would have been sent, instead of sending it
    static class RecordingEmailSender implements EmailSender {
        final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
        volatile boolean failing = false;

        @Override
        public void send(EmailMessage message) {
            if (failing) {
                throw new IllegalStateException("mail server down");
            }
            sent.add(message);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    @Autowired private RecordingEmailSender emails;
    @Autowired private BookingService bookingService;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Court court;
    private AppUser alice;
    private AppUser bob;

    @BeforeEach
    void setUp() {
        emails.sent.clear();
        emails.failing = false;
        Organisation org = organisationRepository.save(new Organisation("Email Uni", "d", "e@example.com"));
        Venue venue = venueRepository.save(new Venue("Email Venue", "addr", org));
        court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
        alice = newUser("Alice");
        bob = newUser("Bob");
    }

    // Nothing is rolled back between tests, so every email address is unique
    private AppUser newUser(String name) {
        return appUserRepository.save(new AppUser(name, UUID.randomUUID() + "@email.test",
                passwordEncoder.encode("pw"), Role.PUBLIC_USER));
    }

    private int nextHour = 1;

    private Long newSlot() {
        LocalDateTime start = LocalDateTime.now().plusDays(2).withMinute(0).withSecond(0).withNano(0)
                .plusHours(nextHour++);
        return timeSlotRepository.save(new TimeSlot(court, start, start.plusHours(1))).getId();
    }

    // Waits (up to 5 seconds) for an email to this user whose subject starts with this
    private EmailMessage awaitEmail(AppUser to, String subjectStart) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            for (EmailMessage m : emails.sent) {
                if (m.to().equals(to.getEmail()) && m.subject().startsWith(subjectStart)) {
                    return m;
                }
            }
            Thread.sleep(50);
        }
        return fail("No '" + subjectStart + "' email to " + to.getFullName() + ". Sent: " + emails.sent);
    }

    private long emailsTo(AppUser user, String subjectStart) {
        return emails.sent.stream()
                .filter(m -> m.to().equals(user.getEmail()) && m.subject().startsWith(subjectStart))
                .count();
    }

    @Test
    void bookingSendsAConfirmation() throws Exception {
        bookingService.bookSlot(newSlot(), alice.getId());

        EmailMessage email = awaitEmail(alice, "Booking confirmed");
        assertTrue(email.body().contains("Court 1 at Email Venue"));
        assertTrue(email.body().startsWith("Hi Alice"));
    }

    @Test
    void aSeriesThatIsRefusedSendsNothing() throws Exception {
        Long week1 = newSlot();
        Long week2 = timeSlotRepository.save(new TimeSlot(court,
                timeSlotRepository.findById(week1).orElseThrow().getStartTime().plusWeeks(1),
                timeSlotRepository.findById(week1).orElseThrow().getEndTime().plusWeeks(1))).getId();
        bookingService.bookSlot(week2, bob.getId());

        assertThrows(SlotNotAvailableException.class,
                () -> bookingService.bookSeries(List.of(week1, week2), alice.getId()));

        // Something Alice does afterwards does send an email: once it has
        // arrived, the refused series would have too, if it was ever going to
        bookingService.bookSlot(newSlot(), alice.getId());
        awaitEmail(alice, "Booking confirmed");
        assertEquals(0, emailsTo(alice, "Weekly booking confirmed"));
    }

    @Test
    void theWaitlistPromotionEmailsThePersonWhoGotTheSlot() throws Exception {
        Long slot = newSlot();
        Booking booking = bookingService.bookSlot(slot, alice.getId());
        bookingService.joinWaitlist(slot, bob.getId());

        bookingService.cancelBooking(booking.getId(), alice.getId());

        assertTrue(awaitEmail(bob, "A place opened up").body().contains("from the waitlist"));
        awaitEmail(alice, "Booking cancelled");
    }

    @Test
    void cancellingASeriesSendsOneEmail() throws Exception {
        Long week1 = newSlot();
        LocalDateTime start = timeSlotRepository.findById(week1).orElseThrow().getStartTime();
        Long week2 = timeSlotRepository.save(new TimeSlot(court, start.plusWeeks(1), start.plusWeeks(1).plusHours(1))).getId();
        Long seriesId = bookingService.bookSeries(List.of(week1, week2), alice.getId()).get(0).getSeries().getId();
        awaitEmail(alice, "Weekly booking confirmed");

        bookingService.cancelSeries(seriesId, alice.getId());

        EmailMessage email = awaitEmail(alice, "Weekly booking cancelled");
        assertEquals(2, email.body().lines().filter(l -> l.startsWith("  ")).count(), "both weeks listed");
        assertEquals(1, emailsTo(alice, "Weekly booking cancelled"));
    }

    @Test
    void aBrokenMailServerDoesntStopTheBooking() throws Exception {
        emails.failing = true;
        Long slot = newSlot();

        Booking booking = bookingService.bookSlot(slot, alice.getId());

        assertTrue(bookingRepository.findById(booking.getId()).isPresent());
        assertEquals(0, emails.sent.size());
    }
}
