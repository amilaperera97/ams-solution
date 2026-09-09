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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

        Provider provider = new Provider("prov-acc-test", "AWS Provider", "org-acc-test", CloudProviderType.AWS, "ACTIVE", Instant.now(), null);
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

    @Test
    void shouldUpdateAnAccountWithoutBeingResentItsSecrets() throws Exception {
        Organisation org = new Organisation("org-acc-upd", "Update Org", "Desc", "ACTIVE", Instant.now(), null);
        orgRepo.save(org);

        Provider provider = new Provider("prov-acc-upd", "AWS Provider", "org-acc-upd", CloudProviderType.AWS, "ACTIVE", Instant.now(), null);
        providerRepo.save(provider);

        Environment env = new Environment("env-acc-upd", "org-acc-upd", "prov-acc-upd", "PROD", "Desc", "ACTIVE", Instant.now(), null);
        envRepo.save(env);

        String createJson = """
                {
                  "name": "Key Account",
                  "accountId": "123456789012",
                  "authType": "ACCESS_KEY",
                  "accessKeyId": "AKIAIOSFODNN7EXAMPLE",
                  "secretAccessKey": "wJalrXUtnFEMI/K7MDENG",
                  "region": "eu-west-2"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/environments/env-acc-upd/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authType").value("ACCESS_KEY"))
                .andExpect(jsonPath("$.region").value("eu-west-2"))
                // The id is an identifier, not a secret, but it is still masked.
                .andExpect(jsonPath("$.accessKeyId").value("****MPLE"))
                .andExpect(jsonPath("$.secretAccessKey").doesNotExist())
                .andExpect(jsonPath("$.credentialsConfigured").value(true))
                .andReturn().getResponse().getContentAsString();

        String id = response.split("\"id\":\"")[1].split("\"")[0];

        // Only the name and region change; the secret access key is not resent.
        String updateJson = """
                {
                  "name": "Renamed Key Account",
                  "region": "us-east-1"
                }
                """;

        mockMvc.perform(put("/api/v1/accounts/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Key Account"))
                .andExpect(jsonPath("$.region").value("us-east-1"))
                .andExpect(jsonPath("$.authType").value("ACCESS_KEY"))
                .andExpect(jsonPath("$.credentialsConfigured").value(true))
                .andExpect(jsonPath("$.secretAccessKey").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(get("/api/v1/accounts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("123456789012"))
                .andExpect(jsonPath("$.credentialsConfigured").value(true));
    }

    @Test
    void shouldSwitchAnAccountToIamRoleAndReportTheRoleAsConfiguredWithoutRevealingIt() throws Exception {
        Organisation org = new Organisation("org-acc-role", "Role Org", "Desc", "ACTIVE", Instant.now(), null);
        orgRepo.save(org);

        Provider provider = new Provider("prov-acc-role", "AWS Provider", "org-acc-role", CloudProviderType.AWS, "ACTIVE", Instant.now(), null);
        providerRepo.save(provider);

        Environment env = new Environment("env-acc-role", "org-acc-role", "prov-acc-role", "PROD", "Desc", "ACTIVE", Instant.now(), null);
        envRepo.save(env);

        String createJson = """
                {
                  "name": "Token Account",
                  "accountId": "123456789012",
                  "authType": "TOKEN",
                  "token": "a-mock-token"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/environments/env-acc-role/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authType").value("TOKEN"))
                .andExpect(jsonPath("$.roleArnConfigured").value(false))
                .andReturn().getResponse().getContentAsString();

        String id = response.split("\"id\":\"")[1].split("\"")[0];

        String updateJson = """
                {
                  "authType": "IAM_ROLE",
                  "roleArn": "arn:aws:iam::123456789012:role/CertificateDiscoveryRole",
                  "externalId": "shared-external-id",
                  "region": "eu-west-2"
                }
                """;

        mockMvc.perform(put("/api/v1/accounts/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authType").value("IAM_ROLE"))
                .andExpect(jsonPath("$.region").value("eu-west-2"))
                .andExpect(jsonPath("$.roleArnConfigured").value(true))
                .andExpect(jsonPath("$.externalIdConfigured").value(true))
                .andExpect(jsonPath("$.roleArn").doesNotExist())
                .andExpect(jsonPath("$.externalId").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    /** The account form reads this to decide whether TOKEN is offered for AWS. */
    @Test
    void shouldReportTheConfiguredProviderModes() throws Exception {
        mockMvc.perform(get("/api/v1/config/cloud-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.AWS.mode").value("MOCK"))
                .andExpect(jsonPath("$.AZURE.mode").value("MOCK"))
                .andExpect(jsonPath("$.GCP.mode").value("MOCK"));
    }
}
