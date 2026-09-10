package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.domain.model.ResourceTag;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AbstractAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsCertificateReferenceResolver;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.cloudfront.model.DistributionList;
import software.amazon.awssdk.services.cloudfront.model.DistributionSummary;
import software.amazon.awssdk.services.cloudfront.model.ListDistributionsRequest;
import software.amazon.awssdk.services.cloudfront.model.ListDistributionsResponse;
import software.amazon.awssdk.services.cloudfront.model.ListTagsForResourceRequest;
import software.amazon.awssdk.services.cloudfront.model.ViewerCertificate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service 5: certificates serving CloudFront distributions.
 *
 * <p>Declared GLOBAL, so a ten-region scan runs this once rather than ten times.
 * The certificate itself always lives in ACM in us-east-1 regardless of where
 * the distribution's origins are - the resolver reads the region out of the ARN,
 * so that is handled without special-casing here.
 *
 * <p>A distribution can also be on the default {@code *.cloudfront.net}
 * certificate, which AWS owns and never exposes. Those are reported as a
 * CloudFront-managed record rather than skipped, so the inventory shows the
 * distribution exists and needs no attention.
 */
@Component
public class AwsCloudFrontDiscoveryStrategy extends AbstractAwsDiscoveryStrategy {

    /** CloudFront's control plane is only addressable in the classic region. */
    private static final String HOME_REGION = "us-east-1";
    /** CloudFront takes its page size as a string, unlike every other paginated AWS API. */
    private static final String PAGE_SIZE = "100";

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "CLOUDFRONT", "CloudFront")
                    .phase(1)
                    .global(HOME_REGION)
                    .aliases("CLOUD_FRONT", "CDN")
                    .requiredPermissions("cloudfront:ListDistributions", "cloudfront:ListTagsForResource",
                            "acm:DescribeCertificate", "iam:GetServerCertificate")
                    .build();

    private final AwsCertificateReferenceResolver resolver;

    public AwsCloudFrontDiscoveryStrategy(CloudProviderProperties providerProperties,
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
        Map<String, Certificate> byKey = new LinkedHashMap<>();
        int distributions = 0;

        try (CloudFrontClient cloudFront = client(CloudFrontClient::builder, context)) {
            String marker = null;
            do {
                ListDistributionsResponse response = cloudFront.listDistributions(ListDistributionsRequest.builder()
                        .marker(marker)
                        .maxItems(PAGE_SIZE)
                        .build());
                DistributionList list = response.distributionList();
                if (list == null || !list.hasItems()) break;

                for (DistributionSummary distribution : list.items()) {
                    if (overResourceLimit(context, distributions, result)) return;
                    distributions++;
                    try {
                        collect(context, cloudFront, distribution, byKey);
                    } catch (SdkException e) {
                        log.warn("Could not read distribution {}: {}", distribution.id(), e.getMessage());
                        result.addError("distribution " + distribution.id() + ": " + e.getMessage());
                    }
                }
                marker = Boolean.TRUE.equals(list.isTruncated()) ? list.nextMarker() : null;
            } while (marker != null && !marker.isBlank());
        }

        byKey.values().forEach(result::addCertificate);
        result.settle("Discovered " + byKey.size() + " certificate(s) across " + distributions + " distribution(s)");
    }

    private void collect(ScanContext context, CloudFrontClient cloudFront,
                         DistributionSummary distribution, Map<String, Certificate> byKey) {
        ViewerCertificate viewer = distribution.viewerCertificate();
        String certificateArn = viewer != null ? certificateArnOf(viewer) : null;

        String key = certificateArn != null ? certificateArn : "cloudfront-default:" + distribution.id();
        Certificate certificate = byKey.computeIfAbsent(key, k -> certificateArn != null
                ? resolver.resolve(context, certificateArn)
                          .orElseGet(() -> resolver.unresolved(context, certificateArn, descriptor().key()))
                : cloudFrontManaged(context, distribution));

        CertificateUsage usage = usage(context, descriptor().key(), distribution.arn(), "Distribution", "ATTACHED");
        usage.setRegion(HOME_REGION);
        certificate.addUsage(usage);

        certificate.addTag(new ResourceTag("cloudfront:distributionId", distribution.id()));
        certificate.addTag(new ResourceTag("cloudfront:domainName", distribution.domainName()));
        if (distribution.aliases() != null && distribution.aliases().hasItems()) {
            certificate.addTag(new ResourceTag("cloudfront:aliases", String.join(",", distribution.aliases().items())));
        }
        if (viewer != null && viewer.minimumProtocolVersionAsString() != null) {
            // Worth surfacing: a current certificate behind TLS 1.0 is still a finding.
            certificate.addTag(new ResourceTag("cloudfront:minimumProtocolVersion",
                    viewer.minimumProtocolVersionAsString()));
        }
        readTags(cloudFront, distribution.arn(), certificate);
    }

    /** ACM ARN when present; otherwise the IAM certificate id, rebuilt into an ARN the resolver understands. */
    private String certificateArnOf(ViewerCertificate viewer) {
        if (viewer.acmCertificateArn() != null && !viewer.acmCertificateArn().isBlank()) {
            return viewer.acmCertificateArn();
        }
        // iamCertificateId is an id, not an ARN; viewer.certificate() carries the ARN form when IAM is in use.
        if (viewer.certificate() != null && !viewer.certificate().isBlank()
                && viewer.certificate().startsWith("arn:")) {
            return viewer.certificate();
        }
        return null;
    }

    /**
     * The shared {@code *.cloudfront.net} certificate. AWS manages and rotates it,
     * so there is nothing to renew - but recording it keeps the distribution
     * visible in the inventory instead of looking like a gap in coverage.
     */
    private Certificate cloudFrontManaged(ScanContext context, DistributionSummary distribution) {
        Certificate certificate = newCertificate(context, "CLOUDFRONT_DEFAULT");
        certificate.setRegion(HOME_REGION);
        certificate.setDomain(distribution.domainName());
        certificate.setResource(distribution.arn());
        certificate.setStatus("MANAGED_BY_AWS");
        certificate.setIssuer("Amazon");
        certificate.setAutoRenewal(true);
        return certificate;
    }

    private void readTags(CloudFrontClient cloudFront, String distributionArn, Certificate certificate) {
        try {
            cloudFront.listTagsForResource(ListTagsForResourceRequest.builder().resource(distributionArn).build())
                      .tags().items()
                      .forEach(tag -> certificate.addTag(new ResourceTag(tag.key(), tag.value())));
        } catch (SdkException e) {
            log.debug("Could not read tags for {}: {}", distributionArn, e.getMessage());
        }
    }
}
