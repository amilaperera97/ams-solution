package uk.co.ams.certplatform.infrastructure.discovery.aws.phase2;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.cloud.aws.UnimplementedAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Service 7: ECS and Fargate.
 *
 * <p>Two separate problems wearing one name. Task definitions reference
 * certificates indirectly - through a Secrets Manager or SSM Parameter
 * Store ARN mounted as a secret, or baked into the image - while the
 * load balancer in front of a service holds the certificate that is
 * actually served. The first needs task definition inspection, the
 * second is already covered by the ALB strategy and should be linked to
 * rather than rediscovered.
 * 
 * For tasks on EC2 launch type the container host is reachable through
 * the existing compute scanner; Fargate tasks are not, so certificates
 * baked into a Fargate image can only be found by inspecting the image.
 *
 * <p><b>Not implemented yet.</b> Registered so the service appears in the
 * capabilities API and the scan picker as a known, greyed-out capability with
 * the permissions it will need. To build it: change the base class to
 * {@code AbstractAwsDiscoveryStrategy}, drop {@code notImplemented()} from the
 * descriptor, and write {@code discoverLive}.
 */
@Component
public class AwsEcsDiscoveryStrategy extends UnimplementedAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "ECS", "ECS and Fargate")
                    .phase(2)
                    .aliases("FARGATE", "ECS_FARGATE", "ELASTIC_CONTAINER_SERVICE")
                    .requiredPermissions("ecs:ListClusters", "ecs:ListServices", "ecs:DescribeServices", "ecs:ListTasks", "ecs:DescribeTasks", "ecs:DescribeTaskDefinition", "elasticloadbalancing:DescribeListeners")
                    .notImplemented()
                    .build();

    public AwsEcsDiscoveryStrategy(CloudProviderProperties providerProperties,
                                       SimulatedCertificateFactory simulator,
                                       AwsClientFactory clientFactory) {
        super(providerProperties, simulator, clientFactory);
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
