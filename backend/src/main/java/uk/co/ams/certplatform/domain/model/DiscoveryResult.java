package uk.co.ams.certplatform.domain.model;

import java.util.ArrayList;
import java.util.List;

public class DiscoveryResult {
    private List<Certificate> certificates = new ArrayList<>();
    private String status;
    private String message;
    private List<String> errors = new ArrayList<>();
    
    // Identifiers for provenance
    private String provider;
    private String accountId;
    private String region;
    private String service;

    public DiscoveryResult() {}
    
    public DiscoveryResult(String provider, String accountId, String region, String service) {
        this.provider = provider;
        this.accountId = accountId;
        this.region = region;
        this.service = service;
    }

    public List<Certificate> getCertificates() { return certificates; }
    public void addCertificate(Certificate certificate) { this.certificates.add(certificate); }
    public void addCertificates(List<Certificate> certificates) { this.certificates.addAll(certificates); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getErrors() { return errors; }
    public void addError(String error) { this.errors.add(error); }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
}
