package uk.co.ams.certplatform.infrastructure.discovery.aws.phase2;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.cloud.aws.UnimplementedAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Service 8: EKS and Kubernetes secrets.
 *
 * <p>Kubernetes keeps TLS material in secrets of type kubernetes.io/tls,
 * which is where cert-manager puts everything it issues. Reading them
 * means authenticating to each cluster's API server, not just the AWS
 * control plane: an EKS access entry or aws-auth mapping has to grant
 * the scanning identity read access on secrets, and a cluster with a
 * private endpoint is unreachable without network path.
 * 
 * Worth noting for whoever builds this: listing secret metadata is
 * cheap and safe, but a kubernetes.io/tls secret's certificate lives in
 * the data, so this one genuinely has to read secret content. It should
 * respect DiscoveryOptions.readSecretContent and read only tls.crt,
 * never tls.key.
 *
 * <p><b>Not implemented yet.</b> Registered so the service appears in the
 * capabilities API and the scan picker as a known, greyed-out capability with
 * the permissions it will need. To build it: change the base class to
 * {@code AbstractAwsDiscoveryStrategy}, drop {@code notImplemented()} from the
 * descriptor, and write {@code discoverLive}.
 */
@Component
public class AwsEksDiscoveryStrategy extends UnimplementedAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "EKS", "EKS and Kubernetes secrets")
                    .phase(2)
                    .aliases("KUBERNETES", "K8S", "EKS_SECRETS")
                    .requiredPermissions("eks:ListClusters", "eks:DescribeCluster", "sts:GetCallerIdentity")
                    .notImplemented()
                    .build();

    public AwsEksDiscoveryStrategy(CloudProviderProperties providerProperties,
                                       SimulatedCertificateFactory simulator,
                                       AwsClientFactory clientFactory) {
        super(providerProperties, simulator, clientFactory);
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
