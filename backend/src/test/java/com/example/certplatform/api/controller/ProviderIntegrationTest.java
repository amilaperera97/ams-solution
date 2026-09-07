package com.example.certplatform.api.controller;

import com.example.certplatform.domain.model.Organisation;
import com.example.certplatform.application.port.OrganisationRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ProviderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganisationRepositoryPort orgRepo;

    @Test
    void shouldCreateAndGetProvider() throws Exception {
        // Setup an organisation first
        Organisation org = new Organisation("org-test", "Integration Org", "Desc", "ACTIVE", Instant.now(), null);
        orgRepo.save(org);

        String createJson = """
                {
                  "type": "AWS"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/organisations/org-test/providers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.type").value("AWS"))
                .andExpect(jsonPath("$.organisationId").value("org-test"))
                .andReturn().getResponse().getContentAsString();

        // Extract ID
        String id = response.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/v1/providers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("AWS"));
                
        mockMvc.perform(get("/api/v1/organisations/org-test/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[0].type").value("AWS"));
    }
}
