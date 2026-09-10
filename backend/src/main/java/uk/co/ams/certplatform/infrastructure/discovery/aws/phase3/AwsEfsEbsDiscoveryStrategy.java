package uk.co.ams.certplatform.infrastructure.discovery.aws.phase3;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.cloud.aws.UnimplementedAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Service 13: EFS and EBS volumes.
 *
 * <p>Detached volumes and shared filesystems that no running instance
 * mounts, so the compute scanner never sees them.
 * 
 * There is no API that reads a file out of a volume. Doing this
 * properly means attaching the volume, or mounting the EFS filesystem,
 * from a scanning instance - which makes it the most invasive item on
 * the roadmap and the one that most needs an explicit opt-in. An EFS
 * filesystem that is mounted by a reachable instance is already covered
 * by the EC2 scanner and should not be double-counted here.
 *
 * <p><b>Not implemented yet.</b> Registered so the service appears in the
 * capabilities API and the scan picker as a known, greyed-out capability with
 * the permissions it will need. To build it: change the base class to
 * {@code AbstractAwsDiscoveryStrategy}, drop {@code notImplemented()} from the
 * descriptor, and write {@code discoverLive}.
 */
@Component
public class AwsEfsEbsDiscoveryStrategy extends UnimplementedAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "EFS_EBS", "EFS and EBS volumes")
                    .phase(3)
                    .aliases("EFS", "EBS", "VOLUMES")
                    .requiredPermissions("elasticfilesystem:DescribeFileSystems", "elasticfilesystem:DescribeMountTargets", "ec2:DescribeVolumes", "ec2:DescribeSnapshots")
                    .notImplemented()
                    .build();

    public AwsEfsEbsDiscoveryStrategy(CloudProviderProperties providerProperties,
                                          SimulatedCertificateFactory simulator,
                                          AwsClientFactory clientFactory) {
        super(providerProperties, simulator, clientFactory);
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
