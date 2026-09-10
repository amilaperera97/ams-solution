package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.application.service.CertificateDiscoveryStrategyRegistry;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.shared.config.DiscoveryProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * What this deployment can scan.
 *
 * <p>The scan picker reads this instead of hardcoding a service list, so
 * onboarding a service is a backend-only change: register the strategy and it
 * appears in the UI, correctly labelled, with the right phase and enablement.
 * The previous hardcoded list had already drifted - it offered "ALB" and
 * "API Gateway" while the backend answered to "SECRETS_MANAGER" and nothing
 * matched.
 */
@RestController
@RequestMapping("/api/v1/config")
public class DiscoveryCapabilityController {

    private final CertificateDiscoveryStrategyRegistry registry;
    private final DiscoveryProperties properties;

    public DiscoveryCapabilityController(CertificateDiscoveryStrategyRegistry registry,
                                         DiscoveryProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    /**
     * @param provider optional filter, e.g. {@code ?provider=AWS}
     */
    @GetMapping("/discovery-capabilities")
    public ResponseEntity<List<DiscoveryCapabilityResponse>> capabilities(
            @RequestParam(required = false) CloudProviderType provider) {

        List<DiscoveryServiceDescriptor> descriptors = provider != null
                ? registry.descriptorsFor(provider)
                : registry.descriptors();

        return ResponseEntity.ok(descriptors.stream().map(this::toResponse).toList());
    }

    private DiscoveryCapabilityResponse toResponse(DiscoveryServiceDescriptor descriptor) {
        boolean disabled = properties.isDisabled(descriptor.qualifiedKey(), descriptor.key());
        return new DiscoveryCapabilityResponse(
                descriptor.provider().name(),
                descriptor.key(),
                descriptor.label(),
                descriptor.phase(),
                descriptor.scope().name(),
                descriptor.homeRegion(),
                descriptor.aliases(),
                descriptor.requiredPermissions(),
                descriptor.implemented(),
                disabled,
                // What the picker should actually let an operator tick.
                descriptor.implemented() && !disabled);
    }

    /**
     * @param selectable implemented and not switched off - the only ones worth ticking.
     *                   The unselectable entries are still returned so the UI can show
     *                   the roadmap rather than pretending the service does not exist.
     */
    public record DiscoveryCapabilityResponse(
            String provider,
            String key,
            String label,
            int phase,
            String scope,
            String homeRegion,
            Set<String> aliases,
            List<String> requiredPermissions,
            boolean implemented,
            boolean disabled,
            boolean selectable) {}
}
