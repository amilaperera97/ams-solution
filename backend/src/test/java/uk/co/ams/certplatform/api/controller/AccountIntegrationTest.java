package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Environment;
import uk.co.ams.certplatform.domain.model.Organisation;
import uk.co.ams.certplatform.domain.model.Provider;
import uk.co.ams.certplatform.application.port.EnvironmentRepositoryPort;
import uk.co.ams.certplatform.application.port.OrganisationRepositoryPort;
import uk.co.ams.certplatform.application.port.ProviderRepositoryPort;
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
class AccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganisationRepositoryPort orgRepo;

    @Autowired
    private ProviderRepositoryPort providerRepo;

    @Autowired
    private EnvironmentRepositoryPort envRepo;

    @Test
    void shouldCreateAndGetAccount() throws Exception {
        // Setup dependencies
        Organisation org = new Organisation("org-acc-test", "Integration Org", "Desc", "ACTIVE", Instant.now(), null);
        orgRepo.save(org);

        Provider provider = new Provider("prov-acc-test", "org-acc-test", CloudProviderType.AWS, "ACTIVE", Instant.now(), null);
        providerRepo.save(provider);

        Environment env = new Environment("env-acc-test", "org-acc-test", "prov-acc-test", "PROD", "Desc", "ACTIVE", Instant.now(), null);
        envRepo.save(env);

        String createJson = """
                {
                  "name": "Production Account",
                  "accountId": "123456789012",
                  "authType": "IAM_ROLE",
                  "roleArn": "arn:aws:iam::123456789012:role/Role"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/environments/env-acc-test/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Production Account"))
                .andExpect(jsonPath("$.accountId").value("123456789012"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.roleArn").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        // Extract ID
        String id = response.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/v1/accounts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Production Account"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.roleArn").doesNotExist());
                
        // Test connection
        mockMvc.perform(post("/api/v1/accounts/" + id + "/test-connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.provider").value("AWS"))
                .andExpect(jsonPath("$.accountId").value("123456789012"));
    }
}
