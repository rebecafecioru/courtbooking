package com.courtbooking.service;

import com.courtbooking.service.LoginAttemptService.TooManyAttemptsException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guesses sent all at once must not slip past the limit. If the limiter
 * checked the count first and recorded the failure only after the password
 * check, all of these could pass the check before any was counted.
 */
class LoginAttemptConcurrencyTest {

    private int allowedOutOf(int attempts, LoginAttemptService limiter, IntFunction email) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                int n = i;
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        limiter.tryAcquire("6.6.6.6", email.apply(n));
                        allowed.incrementAndGet();
                    } catch (TooManyAttemptsException ignored) {
                        // refused, as most should be
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : futures) {
                f.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }
        return allowed.get();
    }

    interface IntFunction {
        String apply(int n);
    }

    @Test
    void fiftySimultaneousGuessesAtOneAccountGetExactlyFiveTries() throws Exception {
        for (int round = 0; round < 20; round++) {
            assertEquals(LoginAttemptService.MAX_PER_IP_AND_EMAIL,
                    allowedOutOf(50, new LoginAttemptService(), n -> "victim@test.com"), "round " + round);
        }
    }

    @Test
    void aHundredSimultaneousGuessesAcrossAccountsGetExactlyTwenty() throws Exception {
        for (int round = 0; round < 20; round++) {
            assertEquals(LoginAttemptService.MAX_PER_IP,
                    allowedOutOf(100, new LoginAttemptService(), n -> "user" + n + "@test.com"), "round " + round);
        }
    }
}
