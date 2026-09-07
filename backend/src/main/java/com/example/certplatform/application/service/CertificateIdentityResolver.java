package com.example.certplatform.application.service;

import com.example.certplatform.domain.model.Certificate;
import com.example.certplatform.domain.model.CertificateUsage;
import com.example.certplatform.domain.model.ResourceTag;
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
            String identity = determineIdentity(cert);
            
            if (uniqueCerts.containsKey(identity)) {
                Certificate existing = uniqueCerts.get(identity);
                // Merge usages
                for (CertificateUsage usage : cert.getUsages()) {
                    existing.addUsage(usage);
                }
                // Merge tags (simple approach: just add all, although deduplication might be needed)
                for (ResourceTag tag : cert.getTags()) {
                    existing.addTag(tag);
                }
            } else {
                uniqueCerts.put(identity, cert);
            }
        }
        
        return new ArrayList<>(uniqueCerts.values());
    }

    private String determineIdentity(Certificate cert) {
        if (cert.getFingerprint() != null && !cert.getFingerprint().isBlank()) {
            return "FINGERPRINT:" + cert.getFingerprint();
        }
        if (cert.getSerialNumber() != null && !cert.getSerialNumber().isBlank()) {
            return "SERIAL:" + cert.getSerialNumber();
        }
        // Fallback to domain + provider + account if no unique cryptograph identifier is present
        return "FALLBACK:" + cert.getDomain() + ":" + cert.getProvider() + ":" + cert.getAccountId();
    }
}
