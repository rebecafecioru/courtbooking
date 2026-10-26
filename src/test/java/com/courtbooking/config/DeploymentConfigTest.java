package com.courtbooking.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

// The settings a real deployment changes: which sites may call the API, and
// whether the demo data is created.
class DeploymentConfigTest {

    @Nested
    @SpringBootTest(properties = {
            "app.cors.allowed-origins=https://courts.example.com, https://www.courts.example.com",
            "app.seed-demo-data=false"})
    @AutoConfigureMockMvc
    class LikeAProductionDeployment {

        @Autowired private MockMvc mockMvc;
        @Autowired private ApplicationContext context;

        @Test
        void aConfiguredSiteMayCallTheApi() throws Exception {
            mockMvc.perform(options("/api/organisations")
                            .header("Origin", "https://www.courts.example.com")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(header().string("Access-Control-Allow-Origin", "https://www.courts.example.com"));
        }

        @Test
        void otherSitesMayNot() throws Exception {
            mockMvc.perform(options("/api/organisations")
                            .header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }

        @Test
        void noDemoDataUnlessAskedFor() {
            assertFalse(context.containsBean("devDataSeeder"));
        }
    }

    @Nested
    @SpringBootTest
    class LikeLocalDevelopment {

        @Autowired private ApplicationContext context;

        @Test
        void demoDataIsOnByDefault() {
            assertTrue(context.containsBean("devDataSeeder"));
        }
    }
}
