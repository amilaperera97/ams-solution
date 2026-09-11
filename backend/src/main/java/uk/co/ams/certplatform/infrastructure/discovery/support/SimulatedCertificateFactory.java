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
        String accountId = context.accountId() != null ? context.accountId() : "unknown";
        String region = context.region() != null ? context.region() : "unknown";
        String slug = descriptor.key().toLowerCase(Locale.ROOT).replace('_', '-');
        String seed = descriptor.qualifiedKey() + "|" + accountId + "|" + region;
        String resource = simulatedResource(descriptor, accountId, region, slug);

        CertificateUsage usage = CertificateUsage.builder()
                .service(descriptor.key())
                .resource(resource)
                .resourceType(descriptor.label())
                .usageType("SIMULATED")
                .region(region)
                .account(accountId)
                .build();

        return Certificate.builder()
                .id("cert-" + UUID.nameUUIDFromBytes(seed.getBytes()))
                .provider(descriptor.provider().name())
                .accountId(accountId)
                .region(region)
                .service(descriptor.key())
                .sourceType("SIMULATED")
                .domain(slug + ".example.com")
                .subject("CN=" + slug + ".example.com, O=Example Ltd, C=GB")
                .issuer("CN=Example Issuing CA, O=Example Ltd, C=GB")
                .status("VALID")
                .algorithm("RSA")
                .keySize(2048)
                .autoRenewal(false)
                // Stable per seed, so the same simulated certificate deduplicates across regions.
                .serialNumber(String.format("%016X", (long) seed.hashCode() << 32 | 0xABCDEF01L))
                .fingerprint(simulatedFingerprint(seed))
                .issuedDate(Instant.now().minus(Duration.ofDays(90)))
                .expiryDate(Instant.now().plus(Duration.ofDays(275)))
                .resource(resource)
                .createdAt(Instant.now())
                .tag(new ResourceTag("simulated", "true"))
                .usage(usage)
                .build();
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
