package com.courtbooking.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits password guessing on login.
 * <p>
 * Two limits, each over the last 15 minutes:
 * <ul>
 *   <li>5 failed logins for one email from one IP address. Not per email
 *       alone: that would let anyone lock someone else out just by typing
 *       wrong passwords for their address.</li>
 *   <li>20 failed logins from one IP address, whatever the email, so one
 *       machine can't try a common password against many accounts.</li>
 * </ul>
 * Correct under concurrency: an attempt reserves its place before the
 * password is checked (check and record are one atomic step per key), and
 * gives it back if the login succeeds. Checking first and recording the
 * failure afterwards would let 50 guesses sent at once all get past the
 * check before any of them was counted.
 * <p>
 * Kept in memory, so it's per server and starts empty after a restart.
 * That's fine for one instance; several instances behind a load balancer
 * would need a shared store (e.g. Redis or a database table) instead.
 */
@Service
public class LoginAttemptService {

    static final int MAX_PER_IP_AND_EMAIL = 5;
    static final int MAX_PER_IP = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    // Key -> times of the attempts still counted against it, oldest first
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    // For tests, to move time forward
    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /**
     * A reserved login attempt. Pass it to {@link #succeeded} if the
     * password was right; otherwise it stays counted as a failure.
     */
    public record Attempt(String ipKey, String pairKey, Instant at) {
    }

    /** Thrown when an attempt isn't allowed yet; says how long to wait. */
    public static class TooManyAttemptsException extends RuntimeException {
        private final Duration retryAfter;

        public TooManyAttemptsException(Duration retryAfter) {
            super("Too many failed login attempts. Please try again in "
                    + Math.max(1, (retryAfter.toSeconds() + 59) / 60) + " minutes.");
            this.retryAfter = retryAfter;
        }

        public Duration getRetryAfter() {
            return retryAfter;
        }
    }

    // Reserves a place for this login attempt, or throws if either limit is reached.
    public Attempt tryAcquire(String ip, String email) {
        Instant now = clock.instant();
        String ipKey = "ip:" + ip;
        String pairKey = "pair:" + ip + "|" + email.trim().toLowerCase(Locale.ROOT);

        reserve(ipKey, MAX_PER_IP, now);
        try {
            reserve(pairKey, MAX_PER_IP_AND_EMAIL, now);
        } catch (TooManyAttemptsException e) {
            release(ipKey, now); // this attempt isn't happening, so don't count it against the IP
            throw e;
        }
        return new Attempt(ipKey, pairKey, now);
    }

    // The password was right: this attempt wasn't a failed guess, and earlier
    // failures for this email from this IP no longer count (typos happen).
    public void succeeded(Attempt attempt) {
        release(attempt.ipKey(), attempt.at());
        attempts.remove(attempt.pairKey());
    }

    // Atomically: forget attempts older than the window, then either refuse
    // (the key is full) or record this attempt.
    private void reserve(String key, int max, Instant now) {
        Duration[] refusedFor = {null};
        attempts.compute(key, (k, times) -> {
            Deque<Instant> recent = times != null ? times : new ArrayDeque<>();
            dropExpired(recent, now);
            if (recent.size() >= max) {
                // Allowed again once the oldest counted attempt leaves the window
                refusedFor[0] = Duration.between(now, recent.peekFirst().plus(WINDOW));
            } else {
                recent.addLast(now);
            }
            return recent.isEmpty() ? null : recent;
        });
        if (refusedFor[0] != null) {
            throw new TooManyAttemptsException(refusedFor[0]);
        }
    }

    private void release(String key, Instant at) {
        attempts.computeIfPresent(key, (k, times) -> {
            times.removeLastOccurrence(at);
            return times.isEmpty() ? null : times;
        });
    }

    private void dropExpired(Deque<Instant> times, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
            times.removeFirst();
        }
    }

    // Every few minutes, forget keys with nothing left in the window, so
    // memory doesn't grow with every IP and email ever tried
    @Scheduled(fixedDelay = 5, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void removeExpired() {
        Instant now = clock.instant();
        for (String key : attempts.keySet()) {
            attempts.computeIfPresent(key, (k, times) -> {
                dropExpired(times, now);
                return times.isEmpty() ? null : times;
            });
        }
    }

    // How many keys are being tracked (for tests)
    int trackedKeys() {
        return attempts.size();
    }
}
