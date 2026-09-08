package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.OrganisationRepositoryPort;
import uk.co.ams.certplatform.domain.model.Organisation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrganisationServiceTest {

    @Mock
    private OrganisationRepositoryPort organisationRepositoryPort;

    @InjectMocks
    private OrganisationService organisationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCreateOrganisation() {
        Organisation org = new Organisation(null, "Test Org", "Description", "ACTIVE", null, null);
        Organisation savedOrg = new Organisation("org-123", "Test Org", "Description", "ACTIVE", null, null);
        
        when(organisationRepositoryPort.save(any(Organisation.class))).thenReturn(savedOrg);

        Organisation result = organisationService.createOrganisation("Test Org", "Description");

        assertNotNull(result);
        assertEquals("org-123", result.getId());
        assertEquals("Test Org", result.getName());
        verify(organisationRepositoryPort, times(1)).save(any(Organisation.class));
    }

    @Test
    void shouldGetOrganisationById() {
        Organisation savedOrg = new Organisation("org-123", "Test Org", "Description", "ACTIVE", null, null);
        when(organisationRepositoryPort.findById("org-123")).thenReturn(Optional.of(savedOrg));

        Optional<Organisation> result = organisationService.getOrganisation("org-123");

        assertTrue(result.isPresent());
        assertEquals("org-123", result.get().getId());
    }
}
