package com.courtbooking.controller;

import com.courtbooking.model.Role;
import com.courtbooking.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository appUserRepository;

    @Test
    void registerIgnoresARequestedRoleAndCreatesAPublicUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Sneaky", "email": "sneaky@test.com",
                                 "password": "pw", "role": "SUPER_ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PUBLIC_USER"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertEquals(Role.PUBLIC_USER,
                appUserRepository.findByEmail("sneaky@test.com").orElseThrow().getRole());
    }

    @Test
    void registeringAnEmailTwiceIsAConflict() throws Exception {
        String body = """
                {"fullName": "Twice", "email": "twice@test.com", "password": "pw"}
                """;
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void loginTellsAnOrgAdminWhichOrganisationTheyManage() throws Exception {
        // The demo org admin from DevDataSeeder
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "admin@leicester-demo.ac.uk", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ORG_ADMIN"))
                .andExpect(jsonPath("$.organisationId").isNumber());
    }
}
