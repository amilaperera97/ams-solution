package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AbstractAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsArns;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsCertificateReferenceResolver;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.CertificateDetail;
import software.amazon.awssdk.services.acm.model.CertificateSummary;
import software.amazon.awssdk.services.acm.model.DescribeCertificateRequest;
import software.amazon.awssdk.services.acm.model.ListCertificatesRequest;

/**
 * Service 1: AWS Certificate Manager.
 *
 * <p>The richest source in the estate, because ACM also reports which resources
 * are serving each certificate. Those {@code inUseBy} ARNs are recorded as
 * usages, which is what lets the ALB, CloudFront and API Gateway strategies
 * agree with this one instead of double-counting.
 */
@Component
public class AwsAcmDiscoveryStrategy extends AbstractAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "ACM", "Certificate Manager (ACM)")
                    .phase(1)
                    .aliases("AWS_ACM", "CERTIFICATE_MANAGER")
                    .requiredPermissions("acm:ListCertificates", "acm:DescribeCertificate",
                            "acm:ListTagsForCertificate", "acm:GetCertificate")
                    .build();

    private final AwsCertificateReferenceResolver resolver;

    public AwsAcmDiscoveryStrategy(CloudProviderProperties providerProperties,
                                   SimulatedCertificateFactory simulator,
                                   AwsClientFactory clientFactory,
                                   AwsCertificateReferenceResolver resolver) {
        super(providerProperties, simulator, clientFactory);
        this.resolver = resolver;
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected void discoverLive(ScanContext context, DiscoveryResult result) {
        String region = effectiveRegion(context);
        int found = 0;

        try (AcmClient acm = client(AcmClient::builder, context)) {
            for (CertificateSummary summary : acm.listCertificatesPaginator(ListCertificatesRequest.builder().build())
                                                 .certificateSummaryList()) {
                if (overResourceLimit(context, found, result)) break;
                try {
                    CertificateDetail detail = acm.describeCertificate(DescribeCertificateRequest.builder()
                            .certificateArn(summary.certificateArn())
                            .build()).certificate();
                    result.addCertificate(toCertificate(context, detail, region));
                    found++;
                } catch (SdkException e) {
                    // One unreadable certificate should not sink the whole region.
                    log.warn("Could not describe {}: {}", summary.certificateArn(), e.getMessage());
                    result.addError("describe " + summary.certificateArn() + ": " + e.getMessage());
                }
            }
        }
        result.settle("Discovered " + found + " ACM certificate(s) in " + region);
    }

    private Certificate toCertificate(ScanContext context, CertificateDetail detail, String region) {
        // The ARN-to-certificate mapping lives in the resolver so that the load
        // balancer and CloudFront strategies produce identical records for the
        // same certificate and the deduplicator can merge them.
        Certificate certificate = resolver.fromAcmDetail(detail, context.getAccount(), region);

        if (detail.hasInUseBy() && !detail.inUseBy().isEmpty()) {
            // ACM knows every resource serving this certificate - ELB, CloudFront,
            // API Gateway - so the attachment graph is available without asking
            // each of those services in turn.
            for (String resourceArn : detail.inUseBy()) {
                CertificateUsage usage = usage(context, AwsArns.serviceOf(resourceArn), resourceArn,
                        AwsArns.serviceOf(resourceArn), "ATTACHED");
                usage.setRegion(AwsArns.regionOf(resourceArn) != null ? AwsArns.regionOf(resourceArn) : region);
                certificate.addUsage(usage);
            }
        } else {
            certificate.addUsage(usage(context, "ACM", detail.certificateArn(), "Certificate", "UNATTACHED"));
        }
        return certificate;
    }
}
