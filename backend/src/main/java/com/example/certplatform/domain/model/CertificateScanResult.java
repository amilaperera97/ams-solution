package com.example.certplatform.domain.model;

import java.util.List;

public class CertificateScanResult {
    private List<Certificate> certificates;
    private String status;
    private String message;

    public CertificateScanResult(List<Certificate> certificates, String status, String message) {
        this.certificates = certificates;
        this.status = status;
        this.message = message;
    }

    public List<Certificate> getCertificates() { return certificates; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
}
