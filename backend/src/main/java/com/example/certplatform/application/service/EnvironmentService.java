package com.example.certplatform.application.service;

import com.example.certplatform.application.port.EnvironmentRepositoryPort;
import com.example.certplatform.application.port.ProviderRepositoryPort;
import com.example.certplatform.domain.model.Environment;
import com.example.certplatform.domain.model.Provider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EnvironmentService {

    private final EnvironmentRepositoryPort environmentRepositoryPort;
    private final ProviderRepositoryPort providerRepositoryPort;

    public EnvironmentService(EnvironmentRepositoryPort environmentRepositoryPort, ProviderRepositoryPort providerRepositoryPort) {
        this.environmentRepositoryPort = environmentRepositoryPort;
        this.providerRepositoryPort = providerRepositoryPort;
    }

    public Environment createEnvironment(String providerId, String name, String description) {
        Provider provider = providerRepositoryPort.findById(providerId)
            .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        Environment environment = new Environment();
        environment.setId("env-" + UUID.randomUUID().toString());
        environment.setOrganisationId(provider.getOrganisationId());
        environment.setProviderId(providerId);
        environment.setName(name);
        environment.setDescription(description);
        environment.setStatus("ACTIVE");
        environment.setCreatedAt(Instant.now());
        environment.setUpdatedAt(Instant.now());

        return environmentRepositoryPort.save(environment);
    }

    public Optional<Environment> getEnvironment(String id) {
        return environmentRepositoryPort.findById(id);
    }

    public List<Environment> getEnvironmentsByProviderId(String providerId) {
        return environmentRepositoryPort.findByProviderId(providerId);
    }
}
