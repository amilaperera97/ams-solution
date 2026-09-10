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

        Account account = context.getAccount();
        String cacheKey = account.getId() + "|" + certificateArn;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && cached.isFresh()) {
            return Optional.ofNullable(cached.certificate()).map(AwsCertificateReferenceResolver::copyOf);
        }

        Optional<Certificate> resolved = lookUp(account, certificateArn);
        if (cache.size() > CACHE_MAX_ENTRIES) cache.clear();
        cache.put(cacheKey, new CacheEntry(resolved.orElse(null), Instant.now().plus(CACHE_TTL)));
        return resolved.map(AwsCertificateReferenceResolver::copyOf);
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
            enrichWithAcmBody(acm, arn, certificate);
            readAcmTags(acm, arn, certificate);
            return Optional.of(certificate);
        }
    }

    /** Maps an ACM CertificateDetail onto the domain model. Shared with the ACM strategy. */
    public Certificate fromAcmDetail(CertificateDetail detail, Account account, String region) {
        Certificate certificate = new Certificate();
        certificate.setId("cert-" + UUID.randomUUID());
        certificate.setProvider(CloudProviderType.AWS.name());
        certificate.setAccountId(account != null ? account.getId() : null);
        certificate.setRegion(region != null ? region : AwsArns.regionOf(detail.certificateArn()));
        certificate.setService("ACM");
        certificate.setSourceType("IMPORTED".equalsIgnoreCase(detail.typeAsString()) ? "ACM_IMPORTED" : "ACM_MANAGED");
        certificate.setDomain(detail.domainName());
        certificate.setStatus(detail.statusAsString());
        certificate.setSubject(detail.subject());
        certificate.setIssuer(detail.issuer());
        certificate.setSerialNumber(normaliseSerial(detail.serial()));
        certificate.setAlgorithm(detail.keyAlgorithmAsString());
        certificate.setKeySize(keySizeOf(detail.keyAlgorithmAsString()));
        certificate.setIssuedDate(detail.notBefore());
        certificate.setExpiryDate(detail.notAfter());
        certificate.setResource(detail.certificateArn());
        certificate.setAutoRenewal("AMAZON_ISSUED".equalsIgnoreCase(detail.typeAsString()));
        certificate.setCreatedAt(Instant.now());
        return certificate;
    }

    /**
     * Pulls the PEM body to compute a real fingerprint. The fingerprint is what
     * lets the deduplicator recognise that the ACM certificate on an ALB and the
     * same certificate found on an instance's disk are one certificate. Optional:
     * {@code acm:GetCertificate} is often not granted, and the serial number is a
     * workable fallback.
     */
    private void enrichWithAcmBody(AcmClient acm, String arn, Certificate certificate) {
        try {
            String pem = acm.getCertificate(GetCertificateRequest.builder().certificateArn(arn).build()).certificate();
            parser.leafOf(pem).ifPresent(parsed -> {
                certificate.setFingerprint(parsed.getFingerprint());
                if (certificate.getSubject() == null) certificate.setSubject(parsed.getSubject());
                if (certificate.getIssuer() == null) certificate.setIssuer(parsed.getIssuer());
                if (certificate.getKeySize() == null) certificate.setKeySize(parsed.getKeySize());
            });
        } catch (SdkException e) {
            log.debug("No certificate body available for {} ({}); falling back to the serial number for identity",
                    arn, e.getMessage());
        }
    }

    private void readAcmTags(AcmClient acm, String arn, Certificate certificate) {
        try {
            acm.listTagsForCertificate(ListTagsForCertificateRequest.builder().certificateArn(arn).build())
               .tags()
               .forEach(tag -> certificate.addTag(new ResourceTag(tag.key(), tag.value())));
        } catch (SdkException e) {
            // Tags are a nice-to-have; a missing acm:ListTagsForCertificate must not fail discovery.
            log.debug("Could not read tags for {}: {}", arn, e.getMessage());
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
        Certificate certificate = parser.leafOf(serverCertificate.certificateBody())
                .orElseGet(Certificate::new);

        certificate.setId("cert-" + UUID.randomUUID());
        certificate.setProvider(CloudProviderType.AWS.name());
        certificate.setAccountId(account != null ? account.getId() : null);
        certificate.setRegion(IAM_REGION);
        certificate.setService("IAM_SERVER_CERTIFICATE");
        certificate.setSourceType("IAM_UPLOADED");
        certificate.setResource(metadata.arn());
        // IAM never renews anything for you - these always expire silently.
        certificate.setAutoRenewal(false);
        certificate.setCreatedAt(Instant.now());
        if (certificate.getExpiryDate() == null) certificate.setExpiryDate(metadata.expiration());
        if (certificate.getDomain() == null) certificate.setDomain(metadata.serverCertificateName());
        if (certificate.getStatus() == null) certificate.setStatus("UNKNOWN");
        certificate.addTag(new ResourceTag("iam:serverCertificateName", metadata.serverCertificateName()));
        return certificate;
    }

    // --- fallbacks -----------------------------------------------------------

    /**
     * A placeholder for a certificate we can see referenced but are not allowed to
     * describe. Recording it keeps the attachment visible - "this listener uses a
     * certificate we cannot read" is far more useful than omitting the listener.
     */
    public Certificate unresolved(ScanContext context, String arn, String service) {
        Certificate certificate = new Certificate();
        certificate.setId("cert-" + UUID.randomUUID());
        certificate.setProvider(CloudProviderType.AWS.name());
        certificate.setAccountId(context.getAccount() != null ? context.getAccount().getId() : null);
        certificate.setRegion(AwsArns.regionOf(arn) != null ? AwsArns.regionOf(arn) : context.getRegion());
        certificate.setService(service);
        certificate.setSourceType("REFERENCE_ONLY");
        certificate.setResource(arn);
        certificate.setDomain(AwsArns.lastSegmentOf(arn));
        certificate.setStatus("UNRESOLVED");
        certificate.setCreatedAt(Instant.now());
        return certificate;
    }

    // --- helpers -------------------------------------------------------------

    /** Cached entries are handed out as copies so a caller's usages do not leak into the cache. */
    private static Certificate copyOf(Certificate source) {
        Certificate copy = new Certificate();
        copy.setId("cert-" + UUID.randomUUID());
        copy.setProvider(source.getProvider());
        copy.setAccountId(source.getAccountId());
        copy.setRegion(source.getRegion());
        copy.setService(source.getService());
        copy.setSourceType(source.getSourceType());
        copy.setDomain(source.getDomain());
        copy.setStatus(source.getStatus());
        copy.setSubject(source.getSubject());
        copy.setIssuer(source.getIssuer());
        copy.setSerialNumber(source.getSerialNumber());
        copy.setFingerprint(source.getFingerprint());
        copy.setAlgorithm(source.getAlgorithm());
        copy.setKeySize(source.getKeySize());
        copy.setIssuedDate(source.getIssuedDate());
        copy.setExpiryDate(source.getExpiryDate());
        copy.setResource(source.getResource());
        copy.setAutoRenewal(source.getAutoRenewal());
        copy.setCreatedAt(source.getCreatedAt());
        source.getTags().forEach(copy::addTag);
        return copy;
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
