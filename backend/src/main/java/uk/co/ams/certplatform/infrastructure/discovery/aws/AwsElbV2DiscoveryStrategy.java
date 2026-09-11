package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ResourceTag;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AbstractAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsCertificateReferenceResolver;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenerCertificatesRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTagsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancerTypeEnum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Services 3 and 4: TLS certificates attached to Elastic Load Balancing v2 listeners.
 *
 * <p>ALBs and NLBs are the same API with a different {@code type}, so the walk -
 * list load balancers, describe their listeners, collect default and SNI
 * certificates - is written once here and specialised by two thin subclasses.
 * That is the pattern to copy whenever two services differ only by a filter.
 *
 * <p>Listeners reference certificates by ARN only, so each one is resolved
 * through {@link AwsCertificateReferenceResolver}: the shared cache means a
 * wildcard certificate on thirty listeners is described once, not thirty times.
 */
public abstract class AwsElbV2DiscoveryStrategy extends AbstractAwsDiscoveryStrategy {

    private final AwsCertificateReferenceResolver resolver;

    protected AwsElbV2DiscoveryStrategy(CloudProviderProperties providerProperties,
                                        SimulatedCertificateFactory simulator,
                                        AwsClientFactory clientFactory,
                                        AwsCertificateReferenceResolver resolver) {
        super(providerProperties, simulator, clientFactory);
        this.resolver = resolver;
    }

    /** Which load balancer type this strategy reports on. */
    protected abstract LoadBalancerTypeEnum loadBalancerType();

    @Override
    protected void discoverLive(ScanContext context, DiscoveryResult.Accumulator result) {
        String region = effectiveRegion(context);
        // One certificate can front several listeners; merge usages as we go so the
        // result holds one record per certificate rather than one per attachment.
        Map<String, Certificate> byArn = new LinkedHashMap<>();
        int loadBalancers = 0;

        try (ElasticLoadBalancingV2Client elb = client(ElasticLoadBalancingV2Client::builder, context)) {
            for (LoadBalancer loadBalancer : elb.describeLoadBalancersPaginator(
                    DescribeLoadBalancersRequest.builder().build()).loadBalancers()) {

                if (loadBalancer.type() != loadBalancerType()) continue;
                if (overResourceLimit(context, loadBalancers, result)) break;
                loadBalancers++;

                try {
                    collectFrom(context, elb, loadBalancer, byArn, region);
                } catch (SdkException e) {
                    // A single load balancer we cannot read should not lose the others.
                    log.warn("Could not read listeners of {}: {}", loadBalancer.loadBalancerArn(), e.getMessage());
                    result.addError("listeners of " + loadBalancer.loadBalancerName() + ": " + e.getMessage());
                }
            }
        }

        byArn.values().forEach(result::addCertificate);
        result.settle("Discovered " + byArn.size() + " certificate(s) across " + loadBalancers + " "
                + descriptor().label() + " in " + region);
    }

    private void collectFrom(ScanContext context, ElasticLoadBalancingV2Client elb, LoadBalancer loadBalancer,
                             Map<String, Certificate> byArn, String region) {
        List<ResourceTag> loadBalancerTags = tagsOf(elb, loadBalancer.loadBalancerArn());

        for (Listener listener : elb.describeListenersPaginator(DescribeListenersRequest.builder()
                .loadBalancerArn(loadBalancer.loadBalancerArn())
                .build()).listeners()) {

            for (String certificateArn : certificateArnsOf(elb, listener)) {
                Certificate certificate = byArn.computeIfAbsent(certificateArn, arn ->
                        resolver.resolve(context, arn)
                                .orElseGet(() -> resolver.unresolved(context, arn, descriptor().key())));

                // The usage helper already stamps the effective region, which is `region`.
                CertificateUsage usage = usage(context, descriptor().key(), loadBalancer.loadBalancerArn(),
                        descriptor().label(), "ATTACHED");

                // Attribute the load balancer's tags to the certificate so estate
                // filters ("everything owned by team X") work on discovered certs.
                List<ResourceTag> tags = new ArrayList<>(loadBalancerTags);
                tags.add(new ResourceTag("elb:loadBalancerName", loadBalancer.loadBalancerName()));
                tags.add(new ResourceTag("elb:listener", listener.protocolAsString() + ":" + listener.port()));
                if (listener.sslPolicy() != null) {
                    tags.add(new ResourceTag("elb:sslPolicy", listener.sslPolicy()));
                }

                byArn.put(certificateArn, certificate
                        .withUsage(usage)
                        .withTags(tags)
                        .toBuilder()
                        .resource(certificate.resource() != null ? certificate.resource() : certificateArn)
                        .build());
            }
        }
    }

    /**
     * A listener's certificates come from two places: the one or two returned
     * inline on the listener, and the full SNI set behind
     * DescribeListenerCertificates. Reading only the inline set is the usual way
     * to miss every SNI certificate on a multi-tenant load balancer.
     */
    private Set<String> certificateArnsOf(ElasticLoadBalancingV2Client elb, Listener listener) {
        Set<String> arns = new LinkedHashSet<>();
        if (listener.hasCertificates()) {
            listener.certificates().forEach(certificate -> arns.add(certificate.certificateArn()));
        }
        if (arns.isEmpty()) return arns; // A non-TLS listener has none; do not spend the extra call.

        try {
            elb.describeListenerCertificates(DescribeListenerCertificatesRequest.builder()
                       .listenerArn(listener.listenerArn())
                       .build())
               .certificates()
               .forEach(certificate -> arns.add(certificate.certificateArn()));
        } catch (SdkException e) {
            log.debug("Could not list SNI certificates for {}: {}", listener.listenerArn(), e.getMessage());
        }
        arns.remove(null);
        return arns;
    }

    private List<ResourceTag> tagsOf(ElasticLoadBalancingV2Client elb, String loadBalancerArn) {
        List<ResourceTag> tags = new ArrayList<>();
        try {
            elb.describeTags(DescribeTagsRequest.builder().resourceArns(loadBalancerArn).build())
               .tagDescriptions()
               .forEach(description -> description.tags()
                       .forEach(tag -> tags.add(new ResourceTag(tag.key(), tag.value()))));
        } catch (SdkException e) {
            log.debug("Could not read tags for {}: {}", loadBalancerArn, e.getMessage());
        }
        return tags;
    }
}
