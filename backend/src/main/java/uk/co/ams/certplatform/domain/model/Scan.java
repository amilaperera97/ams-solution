package com.example.certplatform.domain.model;

import com.example.certplatform.domain.enums.ScanScopeType;
import com.example.certplatform.domain.enums.ScanState;
import java.time.Instant;
import java.util.List;

public class Scan {
    private String id;
    private String name;
    private ScanScopeType scopeType;
    private List<String> providerIds;
    private List<String> environmentIds;
    private List<String> accountIds;
    private List<String> regions;
    private List<String> services;
    private ScanState status;
    private int progressPercent;
    private int accountsTotal;
    private int accountsCompleted;
    private int certificatesDiscovered;
    private Instant createdAt;
    private Instant updatedAt;

    public Scan() {}

    public void transitionTo(ScanState newState) {
        // State machine logic
        if (this.status == ScanState.COMPLETED || this.status == ScanState.CANCELLED || this.status == ScanState.FAILED) {
            throw new IllegalStateException("Cannot transition from final state " + this.status + " to " + newState);
        }
        if (this.status == ScanState.QUEUED && newState == ScanState.COMPLETED) {
            throw new IllegalStateException("Cannot transition directly from QUEUED to COMPLETED");
        }
        this.status = newState;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ScanScopeType getScopeType() { return scopeType; }
    public void setScopeType(ScanScopeType scopeType) { this.scopeType = scopeType; }

    public List<String> getProviderIds() { return providerIds; }
    public void setProviderIds(List<String> providerIds) { this.providerIds = providerIds; }

    public List<String> getEnvironmentIds() { return environmentIds; }
    public void setEnvironmentIds(List<String> environmentIds) { this.environmentIds = environmentIds; }

    public List<String> getAccountIds() { return accountIds; }
    public void setAccountIds(List<String> accountIds) { this.accountIds = accountIds; }

    public List<String> getRegions() { return regions; }
    public void setRegions(List<String> regions) { this.regions = regions; }

    public List<String> getServices() { return services; }
    public void setServices(List<String> services) { this.services = services; }

    public ScanState getStatus() { return status; }
    public void setStatus(ScanState status) { this.status = status; }

    public int getProgressPercent() { return progressPercent; }
    public void setProgressPercent(int progressPercent) { this.progressPercent = progressPercent; }

    public int getAccountsTotal() { return accountsTotal; }
    public void setAccountsTotal(int accountsTotal) { this.accountsTotal = accountsTotal; }

    public int getAccountsCompleted() { return accountsCompleted; }
    public void setAccountsCompleted(int accountsCompleted) { this.accountsCompleted = accountsCompleted; }

    public int getCertificatesDiscovered() { return certificatesDiscovered; }
    public void setCertificatesDiscovered(int certificatesDiscovered) { this.certificatesDiscovered = certificatesDiscovered; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
