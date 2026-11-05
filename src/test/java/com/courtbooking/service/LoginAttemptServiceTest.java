package com.courtbooking.service;

import com.courtbooking.service.LoginAttemptService.Attempt;
import com.courtbooking.service.LoginAttemptService.TooManyAttemptsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptServiceTest {

    // A clock the test moves forward by hand
    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-11-05T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    private final TestClock clock = new TestClock();
    private final LoginAttemptService limiter = new LoginAttemptService(clock);

    // A failed login: reserve an attempt and never call succeeded()
    private void fail(String ip, String email) {
        limiter.tryAcquire(ip, email);
    }

    private void failTimes(int n, String ip, String email) {
        for (int i = 0; i < n; i++) {
            fail(ip, email);
        }
    }

    @Test
    void fiveFailuresAreAllowedThenTheSixthIsRefused() {
        failTimes(5, "1.1.1.1", "me@test.com");

        TooManyAttemptsException e = assertThrows(TooManyAttemptsException.class,
                () -> limiter.tryAcquire("1.1.1.1", "me@test.com"));
        assertEquals(Duration.ofMinutes(15), e.getRetryAfter());
    }

    @Test
    void theWaitIsUntilTheOldestFailureIsFifteenMinutesOld() {
        fail("1.1.1.1", "me@test.com");
        clock.advance(Duration.ofMinutes(10));
        failTimes(4, "1.1.1.1", "me@test.com");

        TooManyAttemptsException e = assertThrows(TooManyAttemptsException.class,
                () -> limiter.tryAcquire("1.1.1.1", "me@test.com"));
        assertEquals(Duration.ofMinutes(5), e.getRetryAfter());

        clock.advance(Duration.ofMinutes(5));
        assertDoesNotThrow(() -> limiter.tryAcquire("1.1.1.1", "me@test.com"));
    }

    @Test
    void aSuccessfulLoginForgivesEarlierTypos() {
        failTimes(4, "1.1.1.1", "me@test.com");
        Attempt right = limiter.tryAcquire("1.1.1.1", "me@test.com");
        limiter.succeeded(right);

        // A fresh 5 tries
        failTimes(5, "1.1.1.1", "me@test.com");
        assertThrows(TooManyAttemptsException.class, () -> limiter.tryAcquire("1.1.1.1", "me@test.com"));
    }

    @Test
    void theEmailIsMatchedIgnoringCaseAndSpaces() {
        failTimes(5, "1.1.1.1", "Me@Test.com");

        assertThrows(TooManyAttemptsException.class, () -> limiter.tryAcquire("1.1.1.1", " me@test.COM "));
    }

    @Test
    void someoneElsesFailuresDontLockYouOut() {
        // An attacker at another address can't lock out your email
        failTimes(5, "6.6.6.6", "victim@test.com");

        assertDoesNotThrow(() -> limiter.tryAcquire("1.1.1.1", "victim@test.com"));
    }

    @Test
    void oneAddressCanOnlyFailTwentyTimesAcrossAllEmails() {
        for (int i = 0; i < 20; i++) {
            fail("6.6.6.6", "user" + i + "@test.com");
        }

        assertThrows(TooManyAttemptsException.class, () -> limiter.tryAcquire("6.6.6.6", "new@test.com"));
        assertDoesNotThrow(() -> limiter.tryAcquire("1.1.1.1", "new@test.com"), "other addresses are fine");
    }

    @Test
    void refusedAttemptsDontUseUpTheAddressLimit() {
        failTimes(5, "1.1.1.1", "me@test.com");
        // Hammering the locked email is refused without counting against the IP...
        for (int i = 0; i < 30; i++) {
            assertThrows(TooManyAttemptsException.class, () -> limiter.tryAcquire("1.1.1.1", "me@test.com"));
        }
        // ...so 15 more failures (20 in all) are still allowed for other emails
        for (int i = 0; i < 15; i++) {
            fail("1.1.1.1", "other" + i + "@test.com");
        }
        assertThrows(TooManyAttemptsException.class, () -> limiter.tryAcquire("1.1.1.1", "last@test.com"));
    }

    @Test
    void successfulLoginsDontCountAgainstTheAddress() {
        // e.g. a whole office behind one IP, logging in all morning
        for (int i = 0; i < 100; i++) {
            limiter.succeeded(limiter.tryAcquire("1.1.1.1", "user" + i + "@test.com"));
        }

        assertDoesNotThrow(() -> limiter.tryAcquire("1.1.1.1", "one-more@test.com"));
    }

    @Test
    void oldEntriesAreForgottenSoMemoryDoesNotGrowForever() {
        failTimes(3, "1.1.1.1", "me@test.com");
        assertEquals(2, limiter.trackedKeys()); // the IP and the IP+email pair

        clock.advance(Duration.ofMinutes(16));
        limiter.removeExpired();

        assertEquals(0, limiter.trackedKeys());
    }
}
