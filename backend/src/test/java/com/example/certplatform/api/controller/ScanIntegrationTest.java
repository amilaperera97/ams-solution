package com.example.certplatform.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ScanIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateAndQueueScan() throws Exception {
        String createJson = """
                {
                  "name": "Production Scan",
                  "scopeType": "ACCOUNT",
                  "accountIds": ["acc-test-123"]
                }
                """;

        String response = mockMvc.perform(post("/api/v1/scans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();

        // Extract ID
        String id = response.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/v1/scans/" + id + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }
}
