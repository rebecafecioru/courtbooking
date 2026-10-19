package com.courtbooking.controller;

import com.courtbooking.config.JwtUtil;
import com.courtbooking.model.AppUser;
import com.courtbooking.model.Organisation;
import com.courtbooking.model.Role;
import com.courtbooking.repository.AppUserRepository;
import com.courtbooking.repository.OrganisationRepository;
import com.courtbooking.repository.VenueRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Only a SUPER_ADMIN may create organisations.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrganisationControllerTest {

    private static final String SUPER_ADMIN = "super@orgs.test";
    private static final String ORG_ADMIN = "orgadmin@orgs.test";
    private static final String PUBLIC_USER = "player@orgs.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Own Uni", "d", "own@example.com"));
        appUserRepository.save(new AppUser("Super", SUPER_ADMIN, passwordEncoder.encode("pw"), Role.SUPER_ADMIN));
        AppUser orgAdmin = new AppUser("Org Admin", ORG_ADMIN, passwordEncoder.encode("pw"), Role.ORG_ADMIN);
        orgAdmin.setOrganisation(org);
        appUserRepository.save(orgAdmin);
        appUserRepository.save(new AppUser("Player", PUBLIC_USER, passwordEncoder.encode("pw"), Role.PUBLIC_USER));
    }

    private ResultActions create(String caller, String json) throws Exception {
        var request = post("/api/organisations/admin").contentType(MediaType.APPLICATION_JSON).content(json);
        if (caller != null) {
            request.header("Authorization", "Bearer " + jwtUtil.generateToken(caller));
        }
        return mockMvc.perform(request);
    }

    private static final String NEW_ORG =
            "{\"name\":\"New Uni\",\"description\":\"Sports centre\",\"contactEmail\":\"new@example.com\"}";

    private boolean exists(String name) {
        return organisationRepository.findAll().stream().anyMatch(o -> o.getName().equals(name));
    }

    @Test
    void superAdminCanCreateAnOrganisation() throws Exception {
        create(SUPER_ADMIN, NEW_ORG)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Uni"))
                .andExpect(jsonPath("$.contactEmail").value("new@example.com"));

        assertThat(exists("New Uni")).isTrue();
    }

    @Test
    void orgAdminCannotCreateAnOrganisation() throws Exception {
        create(ORG_ADMIN, NEW_ORG).andExpect(status().isForbidden());

        assertThat(exists("New Uni")).isFalse();
    }

    @Test
    void publicUserCannotCreateAnOrganisation() throws Exception {
        create(PUBLIC_USER, NEW_ORG).andExpect(status().isForbidden());

        assertThat(exists("New Uni")).isFalse();
    }

    @Test
    void notLoggedInCannotCreateAnOrganisation() throws Exception {
        int status = create(null, NEW_ORG).andReturn().getResponse().getStatus();

        assertThat(status).isIn(401, 403);
        assertThat(exists("New Uni")).isFalse();
    }

    @Test
    void aBlankNameIsRejected() throws Exception {
        create(SUPER_ADMIN, "{\"name\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void anInvalidContactEmailIsRejected() throws Exception {
        create(SUPER_ADMIN, "{\"name\":\"New Uni\",\"contactEmail\":\"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contactEmail").exists());
    }

    @Test
    void fieldsOutsideTheRequestAreIgnored() throws Exception {
        long venuesBefore = venueRepository.count();

        create(SUPER_ADMIN, "{\"name\":\"New Uni\",\"id\":1,"
                + "\"venues\":[{\"name\":\"Sneaky Venue\",\"address\":\"x\"}]}")
                .andExpect(status().isOk());

        assertThat(venueRepository.count()).isEqualTo(venuesBefore);
    }
}
