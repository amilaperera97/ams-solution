package uk.co.ams.certplatform.application.port;

import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;

/**
 * The one extension point for scanning a cloud service.
 *
 * <p><b>To onboard a service:</b> add a class implementing this interface and
 * annotate it {@code @Component}. Spring collects it, the registry indexes it by
 * the key in its descriptor, the planner schedules it, and the capabilities API
 * shows it in the UI. There is no list to append to and no switch to extend.
 *
 * <p><b>To onboard a cloud provider:</b> add a value to
 * {@link uk.co.ams.certplatform.domain.enums.CloudProviderType}, a
 * {@link CloudProviderAdapter} for connection tests, and one strategy per service.
 * Nothing in the application layer is AWS-aware.
 *
 * <p>Implementations must be thread-safe and stateless: many instances of
 * {@link #discover} run concurrently across accounts and regions. They must never
 * throw for an expected condition - report it through {@link DiscoveryResult}
 * instead - though the executor will catch anything that escapes.
 */
public interface CertificateDiscoveryStrategy {

    /** Identity and behaviour of the service this strategy reads. Must be constant. */
    DiscoveryServiceDescriptor descriptor();

    /** Reads certificates for exactly one account, region and service. Never returns null. */
    DiscoveryResult discover(ScanContext context);
}
