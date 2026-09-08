package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy;
import uk.co.ams.certplatform.application.port.DiscoveryCapability;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ResourceTag;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.CertificateDetail;
import software.amazon.awssdk.services.acm.model.CertificateSummary;
import software.amazon.awssdk.services.acm.model.DescribeCertificateRequest;
import software.amazon.awssdk.services.acm.model.ListCertificatesRequest;
import software.amazon.awssdk.services.acm.model.ListTagsForCertificateRequest;

import java.time.Instant;
import java.util.UUID;

@Component
public class AwsAcmCertificateDiscoveryStrategy implements CertificateDiscoveryStrategy {

    private static final Logger log = LoggerFactory.getLogger(AwsAcmCertificateDiscoveryStrategy.class);

    private final CloudProviderProperties properties;
    private final AwsClientFactory clientFactory;

    public AwsAcmCertificateDiscoveryStrategy(CloudProviderProperties properties, AwsClientFactory clientFactory) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    @Override
    public DiscoveryCapability capability() {
        return new DiscoveryCapability(CloudProviderType.AWS, "ACM", "AwsAcmCertificateDiscovery");
    }

    @Override
    public boolean supports(ScanContext context) {
        return "ACM".equalsIgnoreCase(context.getService());
    }

    @Override
    public DiscoveryResult discover(ScanContext context) {
        return properties.isReal(CloudProviderType.AWS) ? discoverForReal(context) : discoverSimulated(context);
    }

    /** Lists every ACM certificate in the region and describes each one. */
    private DiscoveryResult discoverForReal(ScanContext context) {
        String region = clientFactory.resolveRegion(context.getAccount(), context.getRegion());
        DiscoveryResult result = newResult(context, region);

        try (AcmClient acm = clientFactory.acmClient(context.getAccount(), region)) {
            int found = 0;
            for (CertificateSummary summary : acm.listCertificatesPaginator(ListCertificatesRequest.builder().build())
                                                 .certificateSummaryList()) {
                try {
                    CertificateDetail detail = acm.describeCertificate(DescribeCertificateRequest.builder()
                            .certificateArn(summary.certificateArn())
                            .build()).certificate();
                    result.addCertificate(toCertificate(detail, context, region, acm));
                    found++;
                } catch (SdkException e) {
                    // One unreadable certificate should not sink the whole region.
                    log.warn("Could not describe {}: {}", summary.certificateArn(), e.getMessage());
                    result.addError("describe " + summary.certificateArn() + ": " + e.getMessage());
                }
            }
            result.setStatus("SUCCESS");
            result.setMessage("Discovered " + found + " ACM certificate(s) in " + region);
        } catch (SdkException e) {
            log.warn("ACM discovery failed for account {} in {}: {}", context.getAccount().getId(), region, e.getMessage());
            result.setStatus("FAILED");
            result.addError("AWS call failed: " + e.getMessage());
        } catch (RuntimeException e) {
            log.warn("ACM discovery could not run for account {}: {}", context.getAccount().getId(), e.getMessage());
            result.setStatus("FAILED");
            result.addError(e.getMessage());
        }
        return result;
    }

    private Certificate toCertificate(CertificateDetail detail, ScanContext context, String region, AcmClient acm) {
        Certificate cert = new Certificate();
        cert.setId("cert-" + UUID.randomUUID());
        cert.setProvider(CloudProviderType.AWS.name());
        cert.setAccountId(context.getAccount().getId());
        cert.setRegion(region);
        cert.setService("ACM");
        cert.setSourceType("IMPORTED".equalsIgnoreCase(detail.typeAsString()) ? "ACM_IMPORTED" : "ACM_MANAGED");
        cert.setDomain(detail.domainName());
        cert.setStatus(detail.statusAsString());
        cert.setSubject(detail.subject());
        cert.setIssuer(detail.issuer());
        cert.setSerialNumber(detail.serial());
        cert.setAlgorithm(detail.keyAlgorithmAsString());
        cert.setKeySize(keySizeOf(detail.keyAlgorithmAsString()));
        cert.setIssuedDate(detail.notBefore());
        cert.setExpiryDate(detail.notAfter());
        cert.setResource(detail.certificateArn());
        cert.setAutoRenewal("AMAZON_ISSUED".equalsIgnoreCase(detail.typeAsString()));
        cert.setCreatedAt(Instant.now());

        // ACM reports the ARNs of resources serving the certificate (ELB, CloudFront, API Gateway...).
        if (detail.hasInUseBy()) {
            for (String resourceArn : detail.inUseBy()) {
                CertificateUsage usage = new CertificateUsage();
                usage.setService(serviceOfArn(resourceArn));
                usage.setResource(resourceArn);
                usage.setResourceType(serviceOfArn(resourceArn));
                usage.setUsageType("ATTACHED");
                usage.setRegion(region);
                usage.setAccount(context.getAccount().getId());
                cert.addUsage(usage);
            }
        } else {
            CertificateUsage usage = new CertificateUsage();
            usage.setService("ACM");
            usage.setUsageType("MANAGED");
            usage.setRegion(region);
            usage.setAccount(context.getAccount().getId());
            cert.addUsage(usage);
        }

        try {
            acm.listTagsForCertificate(ListTagsForCertificateRequest.builder()
                    .certificateArn(detail.certificateArn())
                    .build())
               .tags()
               .forEach(tag -> cert.addTag(new ResourceTag(tag.key(), tag.value())));
        } catch (SdkException e) {
            // Tags are a nice-to-have; missing acm:ListTagsForCertificate must not fail discovery.
            log.debug("Could not read tags for {}: {}", detail.certificateArn(), e.getMessage());
        }

        return cert;
    }

    /** "arn:aws:elasticloadbalancing:eu-west-2:123:loadbalancer/app/x" -> "elasticloadbalancing" */
    private static String serviceOfArn(String arn) {
        if (arn == null) return "UNKNOWN";
        String[] parts = arn.split(":");
        return parts.length > 2 && !parts[2].isBlank() ? parts[2] : "UNKNOWN";
    }

    /** RSA_2048 -> 2048, EC_prime256v1 -> 256, EC_secp384r1 -> 384. */
    static Integer keySizeOf(String keyAlgorithm) {
        if (keyAlgorithm == null) return null;
        String algorithm = keyAlgorithm.toUpperCase();
        if (algorithm.startsWith("RSA_")) {
            try {
                return Integer.parseInt(algorithm.substring(4));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return switch (algorithm) {
            case "EC_PRIME256V1" -> 256;
            case "EC_SECP384R1" -> 384;
            case "EC_SECP521R1" -> 521;
            default -> null;
        };
    }

    /** Fabricated certificate used by the dev profile. */
    private DiscoveryResult discoverSimulated(ScanContext context) {
        DiscoveryResult result = newResult(context, context.getRegion());
        try {
            Certificate mockCert = new Certificate();
            mockCert.setId("cert-" + UUID.randomUUID());
            mockCert.setDomain("acm.example.com");
            mockCert.setProvider("AWS");
            mockCert.setAccountId(context.getAccount().getId());
            mockCert.setRegion(context.getRegion());
            mockCert.setService("ACM");
            mockCert.setSourceType("ACM_MANAGED");
            mockCert.setStatus("ISSUED");
            mockCert.setFingerprint("AA:BB:CC:DD:EE");
            mockCert.setIssuedDate(Instant.now().minusSeconds(86400 * 30)); // 30 days ago
            mockCert.setExpiryDate(Instant.now().plusSeconds(86400 * 365)); // 1 year from now

            CertificateUsage usage = new CertificateUsage();
            usage.setService("ACM");
            usage.setUsageType("MANAGED");
            usage.setRegion(context.getRegion());
            usage.setAccount(context.getAccount().getId());
            mockCert.addUsage(usage);

            result.addCertificate(mockCert);
            result.setStatus("SUCCESS");
        } catch (Exception e) {
            result.setStatus("FAILED");
            result.addError(e.getMessage());
        }
        return result;
    }

    private DiscoveryResult newResult(ScanContext context, String region) {
        return new DiscoveryResult(
                CloudProviderType.AWS.name(),
                context.getAccount().getId(),
                region,
                context.getService()
        );
    }
}
