package com.courtbooking.controller;

import com.courtbooking.config.JwtUtil;
import com.courtbooking.model.AppUser;
import com.courtbooking.model.Role;
import com.courtbooking.repository.AppUserRepository;
import com.courtbooking.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private NotificationService notificationService;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private AppUser me;

    @BeforeEach
    void setUp() {
        me = appUserRepository.save(new AppUser("Me", "me@notify.test", passwordEncoder.encode("pw"), Role.PUBLIC_USER));
        AppUser other = appUserRepository.save(
                new AppUser("Other", "other@notify.test", passwordEncoder.encode("pw"), Role.PUBLIC_USER));
        notificationService.notify(me, "First");
        notificationService.notify(me, "Second");
        notificationService.notify(other, "Not mine");
    }

    private String bearer(String email) {
        return "Bearer " + jwtUtil.generateToken(email);
    }

    @Test
    void showsOnlyMyNotificationsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/notifications/me").header("Authorization", bearer("me@notify.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.notifications", hasSize(2)))
                .andExpect(jsonPath("$.notifications[0].message").value("Second"))
                .andExpect(jsonPath("$.notifications[0].read").value(false));
    }

    @Test
    void markingAllReadOnlyAffectsMe() throws Exception {
        mockMvc.perform(post("/api/notifications/me/read").header("Authorization", bearer("me@notify.test")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notifications/me").header("Authorization", bearer("me@notify.test")))
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.notifications[0].read").value(true));
        mockMvc.perform(get("/api/notifications/me").header("Authorization", bearer("other@notify.test")))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void needsLogin() throws Exception {
        mockMvc.perform(get("/api/notifications/me"))
                .andExpect(status().is4xxClientError());
    }
}
