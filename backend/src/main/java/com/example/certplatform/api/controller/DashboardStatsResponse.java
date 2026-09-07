package com.example.certplatform.api.controller;

import java.util.Map;

public class DashboardStatsResponse {
    private long totalProviders;
    private long totalEnvironments;
    private long totalAccounts;
    private long totalCertificates;
    private Map<String, Long> certificateHealth;

    public DashboardStatsResponse(long totalProviders, long totalEnvironments, long totalAccounts, long totalCertificates, Map<String, Long> certificateHealth) {
        this.totalProviders = totalProviders;
        this.totalEnvironments = totalEnvironments;
        this.totalAccounts = totalAccounts;
        this.totalCertificates = totalCertificates;
        this.certificateHealth = certificateHealth;
    }

    public long getTotalProviders() {
        return totalProviders;
    }

    public long getTotalEnvironments() {
        return totalEnvironments;
    }

    public long getTotalAccounts() {
        return totalAccounts;
    }

    public long getTotalCertificates() {
        return totalCertificates;
    }

    public Map<String, Long> getCertificateHealth() {
        return certificateHealth;
    }
}
