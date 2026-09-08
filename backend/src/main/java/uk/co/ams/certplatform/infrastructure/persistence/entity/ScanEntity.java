package uk.co.ams.certplatform.infrastructure.persistence.entity;

import uk.co.ams.certplatform.domain.enums.ScanScopeType;
import uk.co.ams.certplatform.domain.enums.ScanState;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.time.Instant;

@Entity
@Table(name = "scan")
public class ScanEntity {

    @Id
    private String id;
    
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type")
    private ScanScopeType scopeType;
    
    // Simplification for SQLite: store lists as comma-separated strings
    @Column(name = "provider_ids")
    private String providerIds;
    
    @Column(name = "environment_ids")
    private String environmentIds;
    
    @Column(name = "account_ids")
    private String accountIds;
    
    private String regions;
    private String services;
    
    @Enumerated(EnumType.STRING)
    private ScanState status;
    
    @Column(name = "progress_percent")
    private int progressPercent;
    
    @Column(name = "accounts_total")
    private int accountsTotal;
    
    @Column(name = "accounts_completed")
    private int accountsCompleted;
    
    @Column(name = "certificates_discovered")
    private int certificatesDiscovered;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ScanScopeType getScopeType() { return scopeType; }
    public void setScopeType(ScanScopeType scopeType) { this.scopeType = scopeType; }

    public String getProviderIds() { return providerIds; }
    public void setProviderIds(String providerIds) { this.providerIds = providerIds; }

    public String getEnvironmentIds() { return environmentIds; }
    public void setEnvironmentIds(String environmentIds) { this.environmentIds = environmentIds; }

    public String getAccountIds() { return accountIds; }
    public void setAccountIds(String accountIds) { this.accountIds = accountIds; }

    public String getRegions() { return regions; }
    public void setRegions(String regions) { this.regions = regions; }

    public String getServices() { return services; }
    public void setServices(String services) { this.services = services; }

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
