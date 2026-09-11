package uk.co.ams.certplatform.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One discovered certificate, identified across services by its fingerprint.
 *
 * <p>The same certificate bound to several resources is stored once and carries
 * a {@link CertificateUsage} per binding, which is what lets the UI answer "what
 * breaks when this expires?". Discovery adds usages and tags as it finds them,
 * so {@link #withUsage} and {@link #withTag} return an extended copy rather than
 * handing out a mutable list.
 *
 * @param fingerprint  SHA-256 of the DER encoding; the cross-service identity
 * @param sourceType   how it was found, e.g. "ACM", "ON_DISK"
 */
public record Certificate(
        String id,
        String scanId,
        String provider,
        String accountId,
        String environment,
        String region,
        String domain,
        String status,
        String service,
        String resource,
        Instant issuedDate,
        Instant expiryDate,
        String issuer,
        String algorithm,
        Boolean autoRenewal,
        Instant createdAt,
        String fingerprint,
        String serialNumber,
        String subject,
        String sourceType,
        Integer keySize,
        List<CertificateUsage> usages,
        List<ResourceTag> tags
) {

    public Certificate {
        usages = usages == null ? List.of() : List.copyOf(usages);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** Returns a copy carrying one more usage; a null usage is ignored. */
    public Certificate withUsage(CertificateUsage usage) {
        if (usage == null) return this;
        List<CertificateUsage> combined = new ArrayList<>(usages);
        combined.add(usage);
        return toBuilder().usages(combined).build();
    }

    /** Returns a copy carrying one more tag; a null tag is ignored. */
    public Certificate withTag(ResourceTag tag) {
        if (tag == null) return this;
        List<ResourceTag> combined = new ArrayList<>(tags);
        combined.add(tag);
        return toBuilder().tags(combined).build();
    }

    public Certificate withTags(List<ResourceTag> extra) {
        if (extra == null || extra.isEmpty()) return this;
        List<ResourceTag> combined = new ArrayList<>(tags);
        extra.forEach(tag -> { if (tag != null) combined.add(tag); });
        return toBuilder().tags(combined).build();
    }

    public Certificate withId(String id) {
        return toBuilder().id(id).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Starting point for a modified copy - the stand-in for the setters this used to have. */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .scanId(scanId)
                .provider(provider)
                .accountId(accountId)
                .environment(environment)
                .region(region)
                .domain(domain)
                .status(status)
                .service(service)
                .resource(resource)
                .issuedDate(issuedDate)
                .expiryDate(expiryDate)
                .issuer(issuer)
                .algorithm(algorithm)
                .autoRenewal(autoRenewal)
                .createdAt(createdAt)
                .fingerprint(fingerprint)
                .serialNumber(serialNumber)
                .subject(subject)
                .sourceType(sourceType)
                .keySize(keySize)
                .usages(usages)
                .tags(tags);
    }

    public static final class Builder {
        private String id;
        private String scanId;
        private String provider;
        private String accountId;
        private String environment;
        private String region;
        private String domain;
        private String status;
        private String service;
        private String resource;
        private Instant issuedDate;
        private Instant expiryDate;
        private String issuer;
        private String algorithm;
        private Boolean autoRenewal;
        private Instant createdAt;
        private String fingerprint;
        private String serialNumber;
        private String subject;
        private String sourceType;
        private Integer keySize;
        private List<CertificateUsage> usages = new ArrayList<>();
        private List<ResourceTag> tags = new ArrayList<>();

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder scanId(String scanId) { this.scanId = scanId; return this; }
        public Builder provider(String provider) { this.provider = provider; return this; }
        public Builder accountId(String accountId) { this.accountId = accountId; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder region(String region) { this.region = region; return this; }
        public Builder domain(String domain) { this.domain = domain; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder service(String service) { this.service = service; return this; }
        public Builder resource(String resource) { this.resource = resource; return this; }
        public Builder issuedDate(Instant issuedDate) { this.issuedDate = issuedDate; return this; }
        public Builder expiryDate(Instant expiryDate) { this.expiryDate = expiryDate; return this; }
        public Builder issuer(String issuer) { this.issuer = issuer; return this; }
        public Builder algorithm(String algorithm) { this.algorithm = algorithm; return this; }
        public Builder autoRenewal(Boolean autoRenewal) { this.autoRenewal = autoRenewal; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder fingerprint(String fingerprint) { this.fingerprint = fingerprint; return this; }
        public Builder serialNumber(String serialNumber) { this.serialNumber = serialNumber; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder sourceType(String sourceType) { this.sourceType = sourceType; return this; }
        public Builder keySize(Integer keySize) { this.keySize = keySize; return this; }

        public Builder usages(List<CertificateUsage> usages) {
            this.usages = usages == null ? new ArrayList<>() : new ArrayList<>(usages);
            return this;
        }

        public Builder usage(CertificateUsage usage) {
            if (usage != null) this.usages.add(usage);
            return this;
        }

        public Builder tags(List<ResourceTag> tags) {
            this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
            return this;
        }

        public Builder tag(ResourceTag tag) {
            if (tag != null) this.tags.add(tag);
            return this;
        }

        public Certificate build() {
            return new Certificate(id, scanId, provider, accountId, environment, region, domain, status,
                    service, resource, issuedDate, expiryDate, issuer, algorithm, autoRenewal, createdAt,
                    fingerprint, serialNumber, subject, sourceType, keySize, usages, tags);
        }
    }
}
