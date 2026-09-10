package uk.co.ams.certplatform.infrastructure.cloud.aws;

import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;

/**
 * Base for a service that is registered in the catalogue but not built yet.
 *
 * <p>Registering it early is deliberate: the service shows up in the API and the
 * UI as a known, greyed-out capability with the IAM permissions it will need,
 * instead of being invisible until someone writes the code. A subclass supplies
 * only a descriptor marked {@code notImplemented()}.
 *
 * <p>To implement one of these, change the base class to
 * {@link AbstractAwsDiscoveryStrategy}, drop {@code notImplemented()} from the
 * descriptor and write {@code discoverLive}. Nothing else in the platform changes.
 */
public abstract class UnimplementedAwsDiscoveryStrategy extends AbstractAwsDiscoveryStrategy {

    protected UnimplementedAwsDiscoveryStrategy(CloudProviderProperties providerProperties,
                                                SimulatedCertificateFactory simulator,
                                                AwsClientFactory clientFactory) {
        super(providerProperties, simulator, clientFactory);
    }

    @Override
    protected final void discoverLive(ScanContext context, DiscoveryResult result) {
        // Unreachable: AbstractDiscoveryStrategy short-circuits on descriptor().implemented().
        result.notImplemented(descriptor().label() + " discovery is not implemented yet.");
    }

    /** MOCK mode must not invent data for a service that does not exist yet either. */
    @Override
    protected final void simulate(ScanContext context, DiscoveryResult result) {
        result.notImplemented(descriptor().label() + " discovery is not implemented yet.");
    }
}
