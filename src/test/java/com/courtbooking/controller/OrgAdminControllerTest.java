package com.courtbooking.controller;

import com.courtbooking.config.JwtUtil;
import com.courtbooking.model.AppUser;
import com.courtbooking.model.Organisation;
import com.courtbooking.model.Role;
import com.courtbooking.repository.AppUserRepository;
import com.courtbooking.repository.OrganisationRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Only a SUPER_ADMIN may create org admins or give a user an organisation.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrgAdminControllerTest {

    private static final String SUPER_ADMIN = "super@orgadmins.test";
    private static final String ORG_ADMIN = "orgadmin@orgadmins.test";
    private static final String PUBLIC_USER = "player@orgadmins.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Long orgId, otherOrgId;
    private Long superAdminId, orgAdminId, publicUserId;

    @BeforeEach
    void setUp() {
        Organisation org = organisationRepository.save(new Organisation("Org Uni", "d", "org@example.com"));
        Organisation other = organisationRepository.save(new Organisation("Other Uni", "d", "other@example.com"));
        orgId = org.getId();
        otherOrgId = other.getId();

        superAdminId = appUserRepository.save(
                new AppUser("Super", SUPER_ADMIN, passwordEncoder.encode("pw"), Role.SUPER_ADMIN)).getId();
        AppUser orgAdmin = new AppUser("Org Admin", ORG_ADMIN, passwordEncoder.encode("pw"), Role.ORG_ADMIN);
        orgAdmin.setOrganisation(org);
        orgAdminId = appUserRepository.save(orgAdmin).getId();
        publicUserId = appUserRepository.save(
                new AppUser("Player", PUBLIC_USER, passwordEncoder.encode("pw"), Role.PUBLIC_USER)).getId();
    }

    private String bearer(String email) {
        return "Bearer " + jwtUtil.generateToken(email);
    }

    private ResultActions createOrgAdmin(String caller, String json) throws Exception {
        return mockMvc.perform(post("/api/admin/org-admins").header("Authorization", bearer(caller))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String newAdminJson(String email, Long organisationId) {
        return "{\"fullName\":\"New Admin\",\"email\":\"" + email + "\",\"password\":\"secret\",\"organisationId\":"
                + organisationId + "}";
    }

    private ResultActions assign(String caller, Long userId, String json) throws Exception {
        return mockMvc.perform(patch("/api/admin/users/{id}/organisation", userId)
                .header("Authorization", bearer(caller))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private AppUser user(String email) {
        return appUserRepository.findByEmail(email).orElseThrow();
    }

    // --- POST /api/admin/org-admins ---

    @Test
    void superAdminCanCreateOrgAdmin() throws Exception {
        createOrgAdmin(SUPER_ADMIN, newAdminJson("new@orgadmins.test", orgId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ORG_ADMIN"))
                .andExpect(jsonPath("$.organisationId").value(orgId))
                .andExpect(jsonPath("$.password").doesNotExist());

        AppUser created = user("new@orgadmins.test");
        assertThat(created.getRole()).isEqualTo(Role.ORG_ADMIN);
        assertThat(created.getOrganisation().getId()).isEqualTo(orgId);
        assertThat(passwordEncoder.matches("secret", created.getPasswordHash())).isTrue();
    }

    @Test
    void orgAdminCannotCreateOrgAdmin() throws Exception {
        createOrgAdmin(ORG_ADMIN, newAdminJson("new@orgadmins.test", orgId)).andExpect(status().isForbidden());
        assertThat(appUserRepository.existsByEmail("new@orgadmins.test")).isFalse();
    }

    @Test
    void publicUserCannotCreateOrgAdmin() throws Exception {
        createOrgAdmin(PUBLIC_USER, newAdminJson("new@orgadmins.test", orgId)).andExpect(status().isForbidden());
    }

    @Test
    void createOrgAdminForUnknownOrganisationIsNotFound() throws Exception {
        createOrgAdmin(SUPER_ADMIN, newAdminJson("new@orgadmins.test", -1L)).andExpect(status().isNotFound());
        assertThat(appUserRepository.existsByEmail("new@orgadmins.test")).isFalse();
    }

    @Test
    void createOrgAdminWithInvalidBodyIsBadRequest() throws Exception {
        createOrgAdmin(SUPER_ADMIN, "{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"secret\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.organisationId").exists());
    }

    @Test
    void createOrgAdminWithTakenEmailIsConflict() throws Exception {
        createOrgAdmin(SUPER_ADMIN, newAdminJson(PUBLIC_USER, orgId)).andExpect(status().isConflict());
        assertThat(user(PUBLIC_USER).getRole()).isEqualTo(Role.PUBLIC_USER);
    }

    // --- PATCH /api/admin/users/{id}/organisation ---

    @Test
    void superAdminCanMakePublicUserAnOrgAdmin() throws Exception {
        assign(SUPER_ADMIN, publicUserId, "{\"organisationId\":" + otherOrgId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ORG_ADMIN"))
                .andExpect(jsonPath("$.organisationId").value(otherOrgId));

        AppUser promoted = user(PUBLIC_USER);
        assertThat(promoted.getRole()).isEqualTo(Role.ORG_ADMIN);
        assertThat(promoted.getOrganisation().getId()).isEqualTo(otherOrgId);
    }

    @Test
    void superAdminCanMoveOrgAdminToAnotherOrganisation() throws Exception {
        assign(SUPER_ADMIN, orgAdminId, "{\"organisationId\":" + otherOrgId + "}").andExpect(status().isOk());
        assertThat(user(ORG_ADMIN).getOrganisation().getId()).isEqualTo(otherOrgId);
    }

    @Test
    void orgAdminCannotAssignOrganisation() throws Exception {
        assign(ORG_ADMIN, publicUserId, "{\"organisationId\":" + orgId + "}").andExpect(status().isForbidden());
        // Nor move themselves into another organisation
        assign(ORG_ADMIN, orgAdminId, "{\"organisationId\":" + otherOrgId + "}").andExpect(status().isForbidden());

        assertThat(user(PUBLIC_USER).getRole()).isEqualTo(Role.PUBLIC_USER);
        assertThat(user(ORG_ADMIN).getOrganisation().getId()).isEqualTo(orgId);
    }

    @Test
    void assignToUnknownUserIsNotFound() throws Exception {
        assign(SUPER_ADMIN, -1L, "{\"organisationId\":" + orgId + "}").andExpect(status().isNotFound());
    }

    @Test
    void assignUnknownOrganisationIsNotFound() throws Exception {
        assign(SUPER_ADMIN, publicUserId, "{\"organisationId\":-1}").andExpect(status().isNotFound());
        assertThat(user(PUBLIC_USER).getRole()).isEqualTo(Role.PUBLIC_USER);
    }

    @Test
    void assignWithoutOrganisationIdIsBadRequest() throws Exception {
        assign(SUPER_ADMIN, publicUserId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.organisationId").exists());
    }

    @Test
    void superAdminCannotBeDemotedToOrgAdmin() throws Exception {
        assign(SUPER_ADMIN, superAdminId, "{\"organisationId\":" + orgId + "}").andExpect(status().isConflict());
        assertThat(user(SUPER_ADMIN).getRole()).isEqualTo(Role.SUPER_ADMIN);
    }

    @Test
    void newOrgAdminCanManageTheirOrganisation() throws Exception {
        assign(SUPER_ADMIN, publicUserId, "{\"organisationId\":" + orgId + "}").andExpect(status().isOk());
        mockMvc.perform(post("/api/venues/admin").header("Authorization", bearer(PUBLIC_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"V\",\"address\":\"a\",\"organisationId\":" + orgId + "}"))
                .andExpect(status().isOk());
    }
}
