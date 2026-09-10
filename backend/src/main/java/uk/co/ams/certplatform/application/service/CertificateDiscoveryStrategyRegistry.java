package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The catalogue of everything the platform can scan.
 *
 * <p>Built by collecting every {@link CertificateDiscoveryStrategy} bean at
 * startup, so the catalogue can never drift from the code: a service exists
 * because a strategy for it exists. Duplicate keys are rejected at startup
 * rather than silently shadowing one another.
 */
@Component
public class CertificateDiscoveryStrategyRegistry {

    private static final Logger log = LoggerFactory.getLogger(CertificateDiscoveryStrategyRegistry.class);

    /** Keyed by {@code PROVIDER:KEY}; iteration order is provider, then phase, then key. */
    private final Map<String, CertificateDiscoveryStrategy> byQualifiedKey;

    public CertificateDiscoveryStrategyRegistry(List<CertificateDiscoveryStrategy> strategies) {
        Map<String, CertificateDiscoveryStrategy> index = new LinkedHashMap<>();
        strategies.stream()
                .sorted(Comparator.comparing((CertificateDiscoveryStrategy s) -> s.descriptor().provider())
                        .thenComparingInt(s -> s.descriptor().phase())
                        .thenComparing(s -> s.descriptor().key()))
                .forEach(strategy -> {
                    DiscoveryServiceDescriptor descriptor = strategy.descriptor();
                    CertificateDiscoveryStrategy clash = index.put(descriptor.qualifiedKey(), strategy);
                    if (clash != null) {
                        throw new IllegalStateException("Two discovery strategies claim "
                                + descriptor.qualifiedKey() + ": " + clash.getClass().getName()
                                + " and " + strategy.getClass().getName());
                    }
                });
        // Not Map.copyOf: that returns an unordered map, and the sort above is what
        // gives the capabilities API and the default "scan everything" a stable order.
        this.byQualifiedKey = Collections.unmodifiableMap(index);
        log.info("Discovery catalogue: {} service(s) registered - {}", index.size(), index.keySet());
    }

    /** Every registered service, including the ones not implemented yet. */
    public List<DiscoveryServiceDescriptor> descriptors() {
        return byQualifiedKey.values().stream().map(CertificateDiscoveryStrategy::descriptor).toList();
    }

    public List<DiscoveryServiceDescriptor> descriptorsFor(CloudProviderType provider) {
        return descriptors().stream().filter(d -> d.provider() == provider).toList();
    }

    /** Services that will actually return data - what an unqualified "scan everything" means. */
    public List<DiscoveryServiceDescriptor> implementedDescriptorsFor(CloudProviderType provider) {
        return descriptorsFor(provider).stream().filter(DiscoveryServiceDescriptor::implemented).toList();
    }

    /** Resolves a caller-supplied service name (canonical key or alias, any case). */
    public Optional<DiscoveryServiceDescriptor> resolve(CloudProviderType provider, String requestedService) {
        return descriptorsFor(provider).stream().filter(d -> d.matches(requestedService)).findFirst();
    }

    public Optional<CertificateDiscoveryStrategy> strategyFor(DiscoveryServiceDescriptor descriptor) {
        return Optional.ofNullable(byQualifiedKey.get(descriptor.qualifiedKey()));
    }

    public Optional<CertificateDiscoveryStrategy> strategyFor(CloudProviderType provider, String requestedService) {
        return resolve(provider, requestedService).flatMap(this::strategyFor);
    }
}
