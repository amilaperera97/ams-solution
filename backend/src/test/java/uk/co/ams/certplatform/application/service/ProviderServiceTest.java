package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.OrganisationRepositoryPort;
import uk.co.ams.certplatform.application.port.ProviderRepositoryPort;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Organisation;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProviderServiceTest {

    @Mock
    private ProviderRepositoryPort providerRepositoryPort;

    @Mock
    private OrganisationRepositoryPort organisationRepositoryPort;

    @InjectMocks
    private ProviderService providerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCreateProvider() {
        String orgId = "org-123";
        Organisation org = new Organisation(orgId, "Test Org", "Desc", "ACTIVE", null, null);
        Provider savedProvider = new Provider("prov-123", "AWS Provider", orgId, CloudProviderType.AWS, "ACTIVE", null, null);
        
        when(organisationRepositoryPort.findById(orgId)).thenReturn(Optional.of(org));
        when(providerRepositoryPort.save(any(Provider.class))).thenReturn(savedProvider);

        Provider result = providerService.createProvider(orgId, "AWS Provider", CloudProviderType.AWS);

        assertNotNull(result);
        assertEquals("prov-123", result.getId());
        assertEquals(orgId, result.getOrganisationId());
        assertEquals(CloudProviderType.AWS, result.getType());
        verify(providerRepositoryPort, times(1)).save(any(Provider.class));
    }

    @Test
    void shouldThrowExceptionWhenCreatingProviderForInvalidOrganisation() {
        String orgId = "invalid-org";
        when(organisationRepositoryPort.findById(orgId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            providerService.createProvider(orgId, "AWS Provider", CloudProviderType.AWS);
        });
        
        verify(providerRepositoryPort, never()).save(any());
    }

    @Test
    void shouldGetProvidersByOrganisationId() {
        String orgId = "org-123";
        Provider savedProvider = new Provider("prov-123", "AWS Provider", orgId, CloudProviderType.AWS, "ACTIVE", null, null);
        
        when(providerRepositoryPort.findByOrganisationId(orgId)).thenReturn(Collections.singletonList(savedProvider));

        List<Provider> results = providerService.getProvidersByOrganisationId(orgId);

        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertEquals(orgId, results.get(0).getOrganisationId());
    }
}
