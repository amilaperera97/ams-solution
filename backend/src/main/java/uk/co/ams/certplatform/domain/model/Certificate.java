package uk.co.ams.certplatform.domain.model;

import java.time.Instant;

public class Certificate {
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
    
    // Deep Discovery Fields
    private String fingerprint;
    private String serialNumber;
    private String subject;
    private String sourceType;
    private Integer keySize;
    
    private java.util.List<CertificateUsage> usages = new java.util.ArrayList<>();
    private java.util.List<ResourceTag> tags = new java.util.ArrayList<>();

    public Certificate() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getScanId() { return scanId; }
    public void setScanId(String scanId) { this.scanId = scanId; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public Integer getKeySize() { return keySize; }
    public void setKeySize(Integer keySize) { this.keySize = keySize; }

    public java.util.List<CertificateUsage> getUsages() { return usages; }
    public void addUsage(CertificateUsage usage) { this.usages.add(usage); }

    public java.util.List<ResourceTag> getTags() { return tags; }
    public void addTag(ResourceTag tag) { this.tags.add(tag); }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public Instant getIssuedDate() { return issuedDate; }
    public void setIssuedDate(Instant issuedDate) { this.issuedDate = issuedDate; }

    public Instant getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Instant expiryDate) { this.expiryDate = expiryDate; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public Boolean getAutoRenewal() { return autoRenewal; }
    public void setAutoRenewal(Boolean autoRenewal) { this.autoRenewal = autoRenewal; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
