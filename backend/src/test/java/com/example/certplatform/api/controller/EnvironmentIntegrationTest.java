package com.example.certplatform.api.controller;

import com.example.certplatform.domain.enums.CloudProviderType;
import com.example.certplatform.domain.model.Organisation;
import com.example.certplatform.domain.model.Provider;
import com.example.certplatform.application.port.OrganisationRepositoryPort;
import com.example.certplatform.application.port.ProviderRepositoryPort;
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
class EnvironmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganisationRepositoryPort orgRepo;

    @Autowired
    private ProviderRepositoryPort providerRepo;

    @Test
    void shouldCreateAndGetEnvironment() throws Exception {
        // Setup organisation and provider
        Organisation org = new Organisation("org-env-test", "Integration Org", "Desc", "ACTIVE", Instant.now(), null);
        orgRepo.save(org);

        Provider provider = new Provider("prov-env-test", "org-env-test", CloudProviderType.AWS, "ACTIVE", Instant.now(), null);
        providerRepo.save(provider);

        String createJson = """
                {
                  "name": "PROD",
                  "description": "Production Environment"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/providers/prov-env-test/environments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("PROD"))
                .andExpect(jsonPath("$.providerId").value("prov-env-test"))
                .andExpect(jsonPath("$.organisationId").value("org-env-test"))
                .andReturn().getResponse().getContentAsString();

        // Extract ID
        String id = response.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/v1/environments/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("PROD"));
                
        mockMvc.perform(get("/api/v1/providers/prov-env-test/environments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.[0].name").value("PROD"));
    }
}
