package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.OrganisationRepositoryPort;
import uk.co.ams.certplatform.application.port.ProviderRepositoryPort;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Provider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProviderService {

    private final ProviderRepositoryPort providerRepositoryPort;
    private final OrganisationRepositoryPort organisationRepositoryPort;

    public ProviderService(ProviderRepositoryPort providerRepositoryPort, OrganisationRepositoryPort organisationRepositoryPort) {
        this.providerRepositoryPort = providerRepositoryPort;
        this.organisationRepositoryPort = organisationRepositoryPort;
    }

    public Provider createProvider(String organisationId, String name, CloudProviderType type) {
        organisationRepositoryPort.findById(organisationId)
            .orElseThrow(() -> new IllegalArgumentException("Organisation not found: " + organisationId));

        Instant now = Instant.now();
        Provider provider = Provider.builder()
                .id("prov-" + UUID.randomUUID())
                .name(name)
                .organisationId(organisationId)
                .type(type)
                .status("PENDING") // Using PENDING as per your previous example, but it can be ACTIVE
                .createdAt(now)
                .updatedAt(now)
                .build();

        return providerRepositoryPort.save(provider);
    }

    public Optional<Provider> getProvider(String id) {
        return providerRepositoryPort.findById(id);
    }

    public List<Provider> getProvidersByOrganisationId(String organisationId) {
        return providerRepositoryPort.findByOrganisationId(organisationId);
    }
}
