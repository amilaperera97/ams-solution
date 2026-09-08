package uk.co.ams.certplatform.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.time.Instant;

@Entity
@Table(name = "certificate")
public class CertificateEntity {

    @Id
    private String id;
    
    @Column(name = "scan_id")
    private String scanId;
    
    private String provider;
    
    @Column(name = "account_id")
    private String accountId;
    
    private String environment;
    private String region;
    private String domain;
    private String status;
    private String service;
    private String resource;
    
    @Column(name = "issued_date")
    private Instant issuedDate;
    
    @Column(name = "expiry_date")
    private Instant expiryDate;
    
    private String issuer;
    private String algorithm;
    
    @Column(name = "auto_renewal")
    private Boolean autoRenewal;
    
    @Column(name = "created_at")
    private Instant createdAt;

    // Deep Discovery Fields
    private String fingerprint;
    
    @Column(name = "serial_number")
    private String serialNumber;
    
    private String subject;
    
    @Column(name = "source_type")
    private String sourceType;
    
    @Column(name = "key_size")
    private Integer keySize;

    // We store usages and tags as JSON strings for simplicity in SQLite for now,
    // or we can map them as @ElementCollection if supported.
    // For hexagonal architecture with SQLite, @ElementCollection is best if tables are created.
    // However, since we are doing rapid TDD on SQLite, let's store as JSON String for now.
    
    @Column(name = "usages_json", length = 4000)
    private String usagesJson;
    
    @Column(name = "tags_json", length = 2000)
    private String tagsJson;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getScanId() { return scanId; }
    public void setScanId(String scanId) { this.scanId = scanId; }

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

    public String getUsagesJson() { return usagesJson; }
    public void setUsagesJson(String usagesJson) { this.usagesJson = usagesJson; }

    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
}
