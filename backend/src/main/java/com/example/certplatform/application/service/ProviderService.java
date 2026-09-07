package com.example.certplatform.application.service;

import com.example.certplatform.application.port.OrganisationRepositoryPort;
import com.example.certplatform.application.port.ProviderRepositoryPort;
import com.example.certplatform.domain.enums.CloudProviderType;
import com.example.certplatform.domain.model.Provider;
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

        Provider provider = new Provider();
        provider.setId("prov-" + UUID.randomUUID().toString());
        provider.setName(name);
        provider.setOrganisationId(organisationId);
        provider.setType(type);
        provider.setStatus("PENDING"); // Using PENDING as per your previous example, but it can be ACTIVE
        provider.setCreatedAt(Instant.now());
        provider.setUpdatedAt(Instant.now());

        return providerRepositoryPort.save(provider);
    }

    public Optional<Provider> getProvider(String id) {
        return providerRepositoryPort.findById(id);
    }

    public List<Provider> getProvidersByOrganisationId(String organisationId) {
        return providerRepositoryPort.findByOrganisationId(organisationId);
    }
}
