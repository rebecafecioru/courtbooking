package com.courtbooking.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void aDatabaseConstraintIsAConflictWithoutTheSqlDetail() {
        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("could not execute statement",
                        new SQLException("duplicate key value violates unique constraint \"uq_booking_time_slot\"")));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertFalse(String.valueOf(response.getBody().get("message")).contains("uq_booking_time_slot"));
    }

    @Test
    void aLockFailureIsAConflictToRetry() {
        ResponseEntity<Map<String, Object>> response = handler.handleLockFailure(
                new CannotAcquireLockException("deadlock detected"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }
}
