package com.courtbooking.postgres;

import com.courtbooking.model.*;
import com.courtbooking.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The real postgres profile on a fresh database. Starting at all proves
 * Flyway's V1 ran and Hibernate's validation found the entities match it;
 * the tests check the rules the database now enforces by itself.
 */
@PostgresTest
@Transactional
class PostgresSchemaTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;

    private Court court;
    private final LocalDateTime nine = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Schema Uni", "d", "s@example.com"));
        Venue venue = venueRepository.save(new Venue("Schema Venue", "addr", org));
        court = courtRepository.save(new Court("Court 1", "Volleyball", 12, venue));
    }

    @Test
    void flywayRanTheMigrations() {
        String version = jdbc.queryForObject(
                "select version from flyway_schema_history where success order by installed_rank desc limit 1",
                String.class);
        assertEquals("1", version);
    }

    @Test
    void theDatabaseRefusesOverlappingSlotsOnTheSameCourt() {
        timeSlotRepository.saveAndFlush(new TimeSlot(court, nine, nine.plusHours(1)));

        assertThrows(DataIntegrityViolationException.class, () -> timeSlotRepository.saveAndFlush(
                new TimeSlot(court, nine.plusMinutes(30), nine.plusMinutes(90))));
    }

    @Test
    void backToBackSlotsAreFine() {
        timeSlotRepository.saveAndFlush(new TimeSlot(court, nine, nine.plusHours(1)));

        assertDoesNotThrow(() -> timeSlotRepository.saveAndFlush(
                new TimeSlot(court, nine.plusHours(1), nine.plusHours(2))));
    }

    @Test
    void aSlotMustEndAfterItStarts() {
        assertThrows(DataIntegrityViolationException.class, () -> timeSlotRepository.saveAndFlush(
                new TimeSlot(court, nine, nine)));
    }
}
