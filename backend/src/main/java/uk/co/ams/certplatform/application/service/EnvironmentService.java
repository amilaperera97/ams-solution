package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.EnvironmentRepositoryPort;
import uk.co.ams.certplatform.application.port.ProviderRepositoryPort;
import uk.co.ams.certplatform.domain.model.Environment;
import uk.co.ams.certplatform.domain.model.Provider;
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

        Instant now = Instant.now();
        Environment environment = Environment.builder()
                .id("env-" + UUID.randomUUID())
                .organisationId(provider.organisationId())
                .providerId(providerId)
                .name(name)
                .description(description)
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();

        return environmentRepositoryPort.save(environment);
    }

    public Optional<Environment> getEnvironment(String id) {
        return environmentRepositoryPort.findById(id);
    }

    public List<Environment> getEnvironmentsByProviderId(String providerId) {
        return environmentRepositoryPort.findByProviderId(providerId);
    }
}
