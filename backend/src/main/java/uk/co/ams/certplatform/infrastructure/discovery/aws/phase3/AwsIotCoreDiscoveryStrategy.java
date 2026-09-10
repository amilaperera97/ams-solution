package uk.co.ams.certplatform.infrastructure.discovery.aws.phase3;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.cloud.aws.UnimplementedAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Service 14: IoT Core.
 *
 * <p>Device certificates, and usually the largest count in the estate by
 * an order of magnitude - a fleet can hold hundreds of thousands.
 * 
 * That scale is the design constraint. Storing one row per device
 * certificate would swamp the inventory, so this should aggregate by CA
 * and expiry window by default, and only expand individual certificates
 * on request. DescribeCertificate is one call per certificate and will
 * be throttled long before a fleet is enumerated.
 *
 * <p><b>Not implemented yet.</b> Registered so the service appears in the
 * capabilities API and the scan picker as a known, greyed-out capability with
 * the permissions it will need. To build it: change the base class to
 * {@code AbstractAwsDiscoveryStrategy}, drop {@code notImplemented()} from the
 * descriptor, and write {@code discoverLive}.
 */
@Component
public class AwsIotCoreDiscoveryStrategy extends UnimplementedAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "IOT_CORE", "IoT Core")
                    .phase(3)
                    .aliases("IOT", "IOT_CERTIFICATES")
                    .requiredPermissions("iot:ListCertificates", "iot:DescribeCertificate", "iot:ListAttachedPolicies", "iot:ListPrincipalThings")
                    .notImplemented()
                    .build();

    public AwsIotCoreDiscoveryStrategy(CloudProviderProperties providerProperties,
                                           SimulatedCertificateFactory simulator,
                                           AwsClientFactory clientFactory) {
        super(providerProperties, simulator, clientFactory);
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
