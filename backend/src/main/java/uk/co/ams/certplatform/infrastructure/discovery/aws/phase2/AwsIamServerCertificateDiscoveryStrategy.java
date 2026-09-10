package uk.co.ams.certplatform.infrastructure.discovery.aws.phase2;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.cloud.aws.UnimplementedAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Service 11: IAM server certificates.
 *
 * <p>The pre-ACM way of holding a certificate, still fronting classic load
 * balancers and older CloudFront distributions. Nothing renews them and
 * nothing warns about them, which makes a full enumeration valuable
 * even though the count is usually small.
 * 
 * Most of the work already exists: AwsCertificateReferenceResolver
 * resolves individual IAM certificate ARNs for the load balancer and
 * CloudFront strategies, and exposes fromIamServerCertificate for reuse.
 * This strategy needs only the ListServerCertificates walk on top.
 * 
 * IAM is global, so this entry is marked global(us-east-1) and runs once
 * per account rather than once per region.
 *
 * <p><b>Not implemented yet.</b> Registered so the service appears in the
 * capabilities API and the scan picker as a known, greyed-out capability with
 * the permissions it will need. To build it: change the base class to
 * {@code AbstractAwsDiscoveryStrategy}, drop {@code notImplemented()} from the
 * descriptor, and write {@code discoverLive}.
 */
@Component
public class AwsIamServerCertificateDiscoveryStrategy extends UnimplementedAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "IAM_SERVER_CERTIFICATE", "IAM server certificates")
                    .phase(2)
                    .global("us-east-1")
                    .aliases("IAM", "IAM_CERTS", "SERVER_CERTIFICATES")
                    .requiredPermissions("iam:ListServerCertificates", "iam:GetServerCertificate")
                    .notImplemented()
                    .build();

    public AwsIamServerCertificateDiscoveryStrategy(CloudProviderProperties providerProperties,
                                                        SimulatedCertificateFactory simulator,
                                                        AwsClientFactory clientFactory) {
        super(providerProperties, simulator, clientFactory);
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
