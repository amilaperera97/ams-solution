package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.ResourceTag;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CertificateIdentityResolver {

    /**
     * Deduplicates certificates based on fingerprint or ARN/serial number.
     * Combines usages and tags into a single canonical certificate.
     */
    public List<Certificate> resolve(List<Certificate> discoveredCertificates) {
        Map<String, Certificate> uniqueCerts = new LinkedHashMap<>();

        for (Certificate cert : discoveredCertificates) {
            uniqueCerts.merge(determineIdentity(cert), cert, CertificateIdentityResolver::combine);
        }

        return new ArrayList<>(uniqueCerts.values());
    }

    /**
     * Folds a duplicate into the certificate already held for that identity: the
     * canonical one keeps its own fields and gains the duplicate's usages and tags,
     * which is what turns "the same certificate on four load balancers" into one
     * row with four usages.
     */
    private static Certificate combine(Certificate canonical, Certificate duplicate) {
        List<CertificateUsage> usages = new ArrayList<>(canonical.usages());
        usages.addAll(duplicate.usages());

        // Simple approach: just add all, although deduplication might be needed.
        List<ResourceTag> tags = new ArrayList<>(canonical.tags());
        tags.addAll(duplicate.tags());

        return canonical.toBuilder().usages(usages).tags(tags).build();
    }

    private String determineIdentity(Certificate cert) {
        if (cert.fingerprint() != null && !cert.fingerprint().isBlank()) {
            return "FINGERPRINT:" + cert.fingerprint();
        }
        if (cert.serialNumber() != null && !cert.serialNumber().isBlank()) {
            return "SERIAL:" + cert.serialNumber();
        }
        // Fallback to domain + provider + account if no unique cryptograph identifier is present
        return "FALLBACK:" + cert.domain() + ":" + cert.provider() + ":" + cert.accountId();
    }
}
