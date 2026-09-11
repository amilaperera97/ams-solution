package uk.co.ams.certplatform.infrastructure.cloud.aws;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.ResourceTag;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.discovery.support.X509CertificateParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.acm.model.CertificateDetail;
import software.amazon.awssdk.services.acm.model.DescribeCertificateRequest;
import software.amazon.awssdk.services.acm.model.GetCertificateRequest;
import software.amazon.awssdk.services.acm.model.ListTagsForCertificateRequest;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.GetServerCertificateRequest;
import software.amazon.awssdk.services.iam.model.ServerCertificate;
import software.amazon.awssdk.services.iam.model.ServerCertificateMetadata;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a certificate ARN into a fully described certificate.
 *
 * <p>Load balancers, CloudFront distributions and API Gateway domains all report
 * a certificate by ARN and nothing more. Every one of those strategies needs the
 * same two lookups - ACM DescribeCertificate, or IAM GetServerCertificate - so
 * they live here once. Results are cached briefly, because one ACM certificate
 * commonly fronts a dozen listeners and describing it a dozen times per scan is
 * both slow and a good way to get throttled.
 */
@Component
public class AwsCertificateReferenceResolver {

    private static final Logger log = LoggerFactory.getLogger(AwsCertificateReferenceResolver.class);

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int CACHE_MAX_ENTRIES = 2000;
    /** IAM is a global service; its endpoint lives in the classic region. */
    private static final String IAM_REGION = "us-east-1";

    private final AwsClientFactory clientFactory;
    private final X509CertificateParser parser;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public AwsCertificateReferenceResolver(AwsClientFactory clientFactory, X509CertificateParser parser) {
        this.clientFactory = clientFactory;
        this.parser = parser;
    }

    /**
     * Describes the certificate behind an ARN. Returns empty rather than throwing
     * when the ARN is unrecognised or unreadable - a listener whose certificate we
     * cannot describe is still worth recording, just with less detail.
     */
    public Optional<Certificate> resolve(ScanContext context, String certificateArn) {
        if (certificateArn == null || certificateArn.isBlank()) return Optional.empty();

        Account account = context.account();
        String cacheKey = account.id() + "|" + certificateArn;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.isFresh()) {
            return Optional.ofNullable(cached.certificate()).map(AwsCertificateReferenceResolver::freshId);
        }

        Optional<Certificate> resolved = lookUp(account, certificateArn);
        if (cache.size() > CACHE_MAX_ENTRIES) cache.clear();
        cache.put(cacheKey, new CacheEntry(resolved.orElse(null), Instant.now().plus(CACHE_TTL)));
        return resolved.map(AwsCertificateReferenceResolver::freshId);
    }

    private Optional<Certificate> lookUp(Account account, String arn) {
        try {
            if (AwsArns.isAcmCertificate(arn)) return describeAcm(account, arn);
            if (AwsArns.isIamServerCertificate(arn)) return describeIam(account, arn);
            log.debug("Certificate ARN {} is neither ACM nor IAM; recording the reference only", arn);
        } catch (SdkException e) {
            log.debug("Could not describe {}: {}", arn, e.getMessage());
        }
        return Optional.empty();
    }

    // --- ACM -----------------------------------------------------------------

    private Optional<Certificate> describeAcm(Account account, String arn) {
        // ACM is regional and the ARN says which region - a CloudFront certificate
        // is in us-east-1 no matter which region the scan is looking at.
        String region = AwsArns.regionOf(arn);
        try (AcmClient acm = clientFactory.client(AcmClient::builder, account, region)) {
            CertificateDetail detail = acm.describeCertificate(
                    DescribeCertificateRequest.builder().certificateArn(arn).build()).certificate();
            Certificate certificate = fromAcmDetail(detail, account, region);
            certificate = enrichWithAcmBody(acm, arn, certificate);
            certificate = withAcmTags(acm, arn, certificate);
            return Optional.of(certificate);
        }
    }

    /** Maps an ACM CertificateDetail onto the domain model. Shared with the ACM strategy. */
    public Certificate fromAcmDetail(CertificateDetail detail, Account account, String region) {
        return Certificate.builder()
                .id("cert-" + UUID.randomUUID())
                .provider(CloudProviderType.AWS.name())
                .accountId(account != null ? account.id() : null)
                .region(region != null ? region : AwsArns.regionOf(detail.certificateArn()))
                .service("ACM")
                .sourceType("IMPORTED".equalsIgnoreCase(detail.typeAsString()) ? "ACM_IMPORTED" : "ACM_MANAGED")
                .domain(detail.domainName())
                .status(detail.statusAsString())
                .subject(detail.subject())
                .issuer(detail.issuer())
                .serialNumber(normaliseSerial(detail.serial()))
                .algorithm(detail.keyAlgorithmAsString())
                .keySize(keySizeOf(detail.keyAlgorithmAsString()))
                .issuedDate(detail.notBefore())
                .expiryDate(detail.notAfter())
                .resource(detail.certificateArn())
                .autoRenewal("AMAZON_ISSUED".equalsIgnoreCase(detail.typeAsString()))
                .createdAt(Instant.now())
                .build();
    }

    /**
     * Pulls the PEM body to compute a real fingerprint. The fingerprint is what
     * lets the deduplicator recognise that the ACM certificate on an ALB and the
     * same certificate found on an instance's disk are one certificate. Optional:
     * {@code acm:GetCertificate} is often not granted, and the serial number is a
     * workable fallback.
     */
    private Certificate enrichWithAcmBody(AcmClient acm, String arn, Certificate certificate) {
        try {
            String pem = acm.getCertificate(GetCertificateRequest.builder().certificateArn(arn).build()).certificate();
            Optional<Certificate> parsed = parser.leafOf(pem);
            if (parsed.isEmpty()) return certificate;

            Certificate leaf = parsed.get();
            return certificate.toBuilder()
                    .fingerprint(leaf.fingerprint())
                    .subject(certificate.subject() != null ? certificate.subject() : leaf.subject())
                    .issuer(certificate.issuer() != null ? certificate.issuer() : leaf.issuer())
                    .keySize(certificate.keySize() != null ? certificate.keySize() : leaf.keySize())
                    .build();
        } catch (SdkException e) {
            log.debug("No certificate body available for {} ({}); falling back to the serial number for identity",
                    arn, e.getMessage());
            return certificate;
        }
    }

    private Certificate withAcmTags(AcmClient acm, String arn, Certificate certificate) {
        try {
            List<ResourceTag> tags = acm.listTagsForCertificate(
                    ListTagsForCertificateRequest.builder().certificateArn(arn).build())
                .tags().stream()
                .map(tag -> new ResourceTag(tag.key(), tag.value()))
                .toList();
            return certificate.withTags(tags);
        } catch (SdkException e) {
            // Tags are a nice-to-have; a missing acm:ListTagsForCertificate must not fail discovery.
            log.debug("Could not read tags for {}: {}", arn, e.getMessage());
            return certificate;
        }
    }

    // --- IAM server certificates ---------------------------------------------

    /**
     * IAM server certificates predate ACM and still front classic load balancers
     * and some CloudFront distributions. Unlike ACM, IAM hands back the actual
     * certificate body, so everything can be read from the certificate itself.
     */
    private Optional<Certificate> describeIam(Account account, String arn) {
        String name = AwsArns.iamServerCertificateName(arn);
        if (name == null) return Optional.empty();

        try (IamClient iam = clientFactory.client(IamClient::builder, account, IAM_REGION)) {
            ServerCertificate serverCertificate = iam.getServerCertificate(
                    GetServerCertificateRequest.builder().serverCertificateName(name).build()).serverCertificate();
            return Optional.of(fromIamServerCertificate(serverCertificate, account));
        }
    }

    /** Public so the Phase 2 IAM strategy can reuse it when it enumerates all server certificates. */
    public Certificate fromIamServerCertificate(ServerCertificate serverCertificate, Account account) {
        ServerCertificateMetadata metadata = serverCertificate.serverCertificateMetadata();
        Certificate parsed = parser.leafOf(serverCertificate.certificateBody())
                .orElseGet(() -> Certificate.builder().build());

        return parsed.toBuilder()
                .id("cert-" + UUID.randomUUID())
                .provider(CloudProviderType.AWS.name())
                .accountId(account != null ? account.id() : null)
                .region(IAM_REGION)
                .service("IAM_SERVER_CERTIFICATE")
                .sourceType("IAM_UPLOADED")
                .resource(metadata.arn())
                // IAM never renews anything for you - these always expire silently.
                .autoRenewal(false)
                .createdAt(Instant.now())
                .expiryDate(parsed.expiryDate() != null ? parsed.expiryDate() : metadata.expiration())
                .domain(parsed.domain() != null ? parsed.domain() : metadata.serverCertificateName())
                .status(parsed.status() != null ? parsed.status() : "UNKNOWN")
                .tag(new ResourceTag("iam:serverCertificateName", metadata.serverCertificateName()))
                .build();
    }

    // --- fallbacks -----------------------------------------------------------

    /**
     * A placeholder for a certificate we can see referenced but are not allowed to
     * describe. Recording it keeps the attachment visible - "this listener uses a
     * certificate we cannot read" is far more useful than omitting the listener.
     */
    public Certificate unresolved(ScanContext context, String arn, String service) {
        return Certificate.builder()
                .id("cert-" + UUID.randomUUID())
                .provider(CloudProviderType.AWS.name())
                .accountId(context.accountId())
                .region(AwsArns.regionOf(arn) != null ? AwsArns.regionOf(arn) : context.region())
                .service(service)
                .sourceType("REFERENCE_ONLY")
                .resource(arn)
                .domain(AwsArns.lastSegmentOf(arn))
                .status("UNRESOLVED")
                .createdAt(Instant.now())
                .build();
    }

    // --- helpers -------------------------------------------------------------

    /**
     * The cached certificate is immutable, so it can be shared freely; only the id
     * is regenerated, keeping each reference to a shared certificate distinguishable
     * until the deduplicator folds them together.
     */
    private static Certificate freshId(Certificate source) {
        return source.withId("cert-" + UUID.randomUUID());
    }

    private static String normaliseSerial(String serial) {
        if (serial == null) return null;
        // ACM reports colon-separated lowercase hex; the parser produces plain uppercase.
        return serial.replace(":", "").toUpperCase(Locale.ROOT);
    }

    /** RSA_2048 -> 2048, EC_prime256v1 -> 256, EC_secp384r1 -> 384. */
    static Integer keySizeOf(String keyAlgorithm) {
        if (keyAlgorithm == null) return null;
        String algorithm = keyAlgorithm.toUpperCase(Locale.ROOT);
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

    private record CacheEntry(Certificate certificate, Instant expiresAt) {
        boolean isFresh() { return Instant.now().isBefore(expiresAt); }
    }
}
