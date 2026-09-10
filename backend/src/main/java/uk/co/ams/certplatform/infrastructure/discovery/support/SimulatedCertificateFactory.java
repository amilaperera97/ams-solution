package uk.co.ams.certplatform.infrastructure.discovery.support;

import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.domain.model.ResourceTag;
import uk.co.ams.certplatform.domain.model.ScanContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Produces believable certificates for MOCK mode.
 *
 * <p>Written once here rather than copied into every strategy: a newly onboarded
 * service gets working dev-profile behaviour with no extra code, and the shape
 * of simulated data stays consistent across services. The values are
 * deterministic per account/region/service so repeated dev scans deduplicate the
 * way real ones would instead of multiplying.
 */
@Component
public class SimulatedCertificateFactory {

    public Certificate certificateFor(ScanContext context, DiscoveryServiceDescriptor descriptor) {
        String accountId = context.getAccount() != null ? context.getAccount().getId() : "unknown";
        String region = context.getRegion() != null ? context.getRegion() : "unknown";
        String slug = descriptor.key().toLowerCase(Locale.ROOT).replace('_', '-');
        String seed = descriptor.qualifiedKey() + "|" + accountId + "|" + region;

        Certificate certificate = new Certificate();
        certificate.setId("cert-" + UUID.nameUUIDFromBytes(seed.getBytes()));
        certificate.setProvider(descriptor.provider().name());
        certificate.setAccountId(accountId);
        certificate.setRegion(region);
        certificate.setService(descriptor.key());
        certificate.setSourceType("SIMULATED");
        certificate.setDomain(slug + ".example.com");
        certificate.setSubject("CN=" + slug + ".example.com, O=Example Ltd, C=GB");
        certificate.setIssuer("CN=Example Issuing CA, O=Example Ltd, C=GB");
        certificate.setStatus("VALID");
        certificate.setAlgorithm("RSA");
        certificate.setKeySize(2048);
        certificate.setAutoRenewal(false);
        // Stable per seed, so the same simulated certificate deduplicates across regions.
        certificate.setSerialNumber(String.format("%016X", (long) seed.hashCode() << 32 | 0xABCDEF01L));
        certificate.setFingerprint(simulatedFingerprint(seed));
        certificate.setIssuedDate(Instant.now().minus(Duration.ofDays(90)));
        certificate.setExpiryDate(Instant.now().plus(Duration.ofDays(275)));
        certificate.setResource(simulatedResource(descriptor, accountId, region, slug));
        certificate.setCreatedAt(Instant.now());
        certificate.addTag(new ResourceTag("simulated", "true"));

        CertificateUsage usage = new CertificateUsage();
        usage.setService(descriptor.key());
        usage.setResource(certificate.getResource());
        usage.setResourceType(descriptor.label());
        usage.setUsageType("SIMULATED");
        usage.setRegion(region);
        usage.setAccount(accountId);
        certificate.addUsage(usage);

        return certificate;
    }

    private static String simulatedResource(DiscoveryServiceDescriptor descriptor,
                                            String accountId, String region, String slug) {
        return "arn:aws:" + slug + ":" + region + ":" + accountId + ":simulated/" + descriptor.key().toLowerCase(Locale.ROOT);
    }

    private static String simulatedFingerprint(String seed) {
        byte[] digest = UUID.nameUUIDFromBytes(seed.getBytes()).toString().getBytes();
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            if (i > 0) hex.append(':');
            hex.append(String.format("%02X", digest[i % digest.length] ^ i));
        }
        return hex.toString();
    }
}
