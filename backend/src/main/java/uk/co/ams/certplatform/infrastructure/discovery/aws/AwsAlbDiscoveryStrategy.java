package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsCertificateReferenceResolver;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancerTypeEnum;

/** Service 3: certificates on Application Load Balancer HTTPS listeners. */
@Component
public class AwsAlbDiscoveryStrategy extends AwsElbV2DiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "ALB", "Application Load Balancer")
                    .phase(1)
                    .aliases("APPLICATION_LOAD_BALANCER", "ELBV2_APPLICATION", "ELB")
                    .requiredPermissions("elasticloadbalancing:DescribeLoadBalancers",
                            "elasticloadbalancing:DescribeListeners",
                            "elasticloadbalancing:DescribeListenerCertificates",
                            "elasticloadbalancing:DescribeTags",
                            "acm:DescribeCertificate", "iam:GetServerCertificate")
                    .build();

    public AwsAlbDiscoveryStrategy(CloudProviderProperties providerProperties,
                                   SimulatedCertificateFactory simulator,
                                   AwsClientFactory clientFactory,
                                   AwsCertificateReferenceResolver resolver) {
        super(providerProperties, simulator, clientFactory, resolver);
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected LoadBalancerTypeEnum loadBalancerType() {
        return LoadBalancerTypeEnum.APPLICATION;
    }
}
