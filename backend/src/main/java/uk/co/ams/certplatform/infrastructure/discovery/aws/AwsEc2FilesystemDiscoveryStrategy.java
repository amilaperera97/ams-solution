package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AbstractAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.compute.ComputeCertificateScanner;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Service 2: certificates sitting on EC2 instance filesystems.
 *
 * <p>The important one and the one the cloud APIs cannot answer. ACM knows about
 * certificates AWS issued; nothing knows about the certificate someone copied
 * onto an nginx box three years ago, which is exactly the one that expires at
 * 2am on a Sunday.
 *
 * <p>All of the work is in {@link ComputeCertificateScanner} and the SSM
 * executor behind it, so this class is only a catalogue entry plus a delegation.
 * ECS on EC2 and EKS node scanning will be the same three lines against the same
 * scanner.
 */
@Component
public class AwsEc2FilesystemDiscoveryStrategy extends AbstractAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "EC2", "EC2 instance filesystems")
                    .phase(1)
                    .aliases("EC2_FILESYSTEM", "EC2 FILESYSTEM", "COMPUTE")
                    .requiredPermissions("ssm:DescribeInstanceInformation", "ssm:SendCommand",
                            "ssm:GetCommandInvocation", "ec2:DescribeInstances")
                    .build();

    private final ComputeCertificateScanner scanner;

    public AwsEc2FilesystemDiscoveryStrategy(CloudProviderProperties providerProperties,
                                             SimulatedCertificateFactory simulator,
                                             AwsClientFactory clientFactory,
                                             ComputeCertificateScanner scanner) {
        super(providerProperties, simulator, clientFactory);
        this.scanner = scanner;
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected void discoverLive(ScanContext context, DiscoveryResult result) {
        scanner.scan(context, result, descriptor().key(), "EC2 instance");
    }
}
