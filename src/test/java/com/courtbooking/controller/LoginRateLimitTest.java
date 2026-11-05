package com.courtbooking.controller;

import com.courtbooking.model.AppUser;
import com.courtbooking.model.Role;
import com.courtbooking.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginRateLimitTest {

    // The limiter is shared by the whole test run, so each test logs in from
    // its own made-up address and doesn't use up anyone else's attempts
    private static final AtomicInteger nextAddress = new AtomicInteger(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String ip;

    @BeforeEach
    void setUp() {
        ip = "203.0.113." + nextAddress.getAndIncrement();
        appUserRepository.save(new AppUser("Limited", "limited@test.com",
                passwordEncoder.encode("right-password"), Role.PUBLIC_USER));
    }

    private ResultActions login(String fromIp, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(fromIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"limited@test.com\", \"password\": \"" + password + "\"}"));
    }

    @Test
    void aWrongPasswordIs401() throws Exception {
        login(ip, "wrong").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }

    @Test
    void theSixthWrongPasswordIs429WithRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(ip, "wrong").andExpect(status().isUnauthorized());
        }

        login(ip, "wrong")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.message", containsString("try again in 15 minutes")));
    }

    @Test
    void whileBlockedEvenTheRightPasswordIsRefused() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(ip, "wrong");
        }

        // Otherwise the block would tell an attacker when they'd guessed right
        login(ip, "right-password").andExpect(status().isTooManyRequests());
    }

    @Test
    void theRealUserFromAnotherAddressCanStillLogIn() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(ip, "wrong");
        }

        login("198.51.100.7", "right-password")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
