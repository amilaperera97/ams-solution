package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.EnvironmentRepositoryPort;
import uk.co.ams.certplatform.application.port.ProviderRepositoryPort;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Environment;
import uk.co.ams.certplatform.domain.model.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EnvironmentServiceTest {

    @Mock
    private EnvironmentRepositoryPort environmentRepositoryPort;

    @Mock
    private ProviderRepositoryPort providerRepositoryPort;

    @InjectMocks
    private EnvironmentService environmentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCreateEnvironment() {
        String providerId = "prov-123";
        Provider provider = new Provider(providerId, "org-123", CloudProviderType.AWS, "ACTIVE", null, null);
        Environment savedEnv = new Environment("env-123", "org-123", providerId, "Test Env", "Desc", "ACTIVE", null, null);

        when(providerRepositoryPort.findById(providerId)).thenReturn(Optional.of(provider));
        when(environmentRepositoryPort.save(any(Environment.class))).thenReturn(savedEnv);

        Environment result = environmentService.createEnvironment(providerId, "Test Env", "Desc");

        assertNotNull(result);
        assertEquals("env-123", result.getId());
        assertEquals(providerId, result.getProviderId());
        assertEquals("org-123", result.getOrganisationId());
        assertEquals("Test Env", result.getName());
        verify(environmentRepositoryPort, times(1)).save(any(Environment.class));
    }

    @Test
    void shouldThrowExceptionWhenCreatingEnvironmentForInvalidProvider() {
        String providerId = "invalid-prov";
        when(providerRepositoryPort.findById(providerId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            environmentService.createEnvironment(providerId, "Test Env", "Desc");
        });

        verify(environmentRepositoryPort, never()).save(any());
    }

    @Test
    void shouldGetEnvironmentsByProviderId() {
        String providerId = "prov-123";
        Environment savedEnv = new Environment("env-123", "org-123", providerId, "Test Env", "Desc", "ACTIVE", null, null);

        when(environmentRepositoryPort.findByProviderId(providerId)).thenReturn(Collections.singletonList(savedEnv));

        List<Environment> results = environmentService.getEnvironmentsByProviderId(providerId);

        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertEquals(providerId, results.get(0).getProviderId());
    }
}
