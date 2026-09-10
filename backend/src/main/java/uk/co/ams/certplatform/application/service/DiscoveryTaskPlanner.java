package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.domain.model.DiscoveryTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Turns "these accounts, these regions, these services" into the exact list of
 * tasks to run.
 *
 * <p>Separated from the executor because the interesting rules live here and are
 * worth testing on their own: an unrecognised service name is reported rather
 * than silently dropped, an empty service list means every implemented service,
 * and a global service collapses to a single task no matter how many regions
 * were requested.
 */
@Component
public class DiscoveryTaskPlanner {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryTaskPlanner.class);

    private final CertificateDiscoveryStrategyRegistry registry;

    public DiscoveryTaskPlanner(CertificateDiscoveryStrategyRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param tasks     work to dispatch, in a stable order
     * @param unknownServices service names the caller asked for that no strategy claims
     */
    public record Plan(List<DiscoveryTask> tasks, List<String> unknownServices) {}

    public Plan plan(List<AccountTarget> accounts, List<String> requestedRegions, List<String> requestedServices) {
        List<DiscoveryTask> tasks = new ArrayList<>();
        Set<String> unknown = new LinkedHashSet<>();

        for (AccountTarget target : accounts) {
            List<DiscoveryServiceDescriptor> services = resolveServices(target.provider(), requestedServices, unknown);
            List<String> regions = resolveRegions(target.account(), requestedRegions);

            for (DiscoveryServiceDescriptor descriptor : services) {
                if (descriptor.isGlobal()) {
                    // One task only: listing CloudFront or IAM once per region would
                    // return the same resources every time and inflate the results.
                    tasks.add(new DiscoveryTask(target.account(), descriptor, descriptor.homeRegion()));
                } else {
                    for (String region : regions) {
                        tasks.add(new DiscoveryTask(target.account(), descriptor, region));
                    }
                }
            }
        }
        return new Plan(List.copyOf(tasks), List.copyOf(unknown));
    }

    private List<DiscoveryServiceDescriptor> resolveServices(CloudProviderType provider,
                                                             List<String> requested,
                                                             Set<String> unknown) {
        if (requested == null || requested.isEmpty()) {
            return registry.implementedDescriptorsFor(provider);
        }
        List<DiscoveryServiceDescriptor> resolved = new ArrayList<>();
        for (String name : requested) {
            Optional<DiscoveryServiceDescriptor> match = registry.resolve(provider, name);
            if (match.isEmpty()) {
                log.warn("No {} discovery strategy claims service '{}'", provider, name);
                unknown.add(provider.name() + ":" + name);
            } else if (!resolved.contains(match.get())) {
                resolved.add(match.get());
            }
        }
        return resolved;
    }

    /**
     * A scan with no regions falls back to the account's home region, which keeps
     * the old "default" placeholder from reaching the SDK as a literal region name.
     */
    private List<String> resolveRegions(Account account, List<String> requested) {
        List<String> regions = new ArrayList<>();
        if (requested != null) {
            for (String region : requested) {
                if (region != null && !region.isBlank() && !"default".equalsIgnoreCase(region)) {
                    regions.add(region.trim());
                }
            }
        }
        if (regions.isEmpty() && account.getRegion() != null && !account.getRegion().isBlank()) {
            regions.add(account.getRegion());
        }
        if (regions.isEmpty()) {
            // The client factory substitutes the configured default for a null region.
            regions.add(null);
        }
        return regions;
    }

    /** An account paired with the cloud it actually belongs to. */
    public record AccountTarget(Account account, CloudProviderType provider) {}
}
