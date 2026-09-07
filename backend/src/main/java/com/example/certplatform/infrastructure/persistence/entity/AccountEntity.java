package com.example.certplatform.infrastructure.persistence.entity;

import com.example.certplatform.domain.enums.AccountAuthType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.time.Instant;

@Entity
@Table(name = "account")
public class AccountEntity {

    @Id
    private String id;
    
    @Column(name = "organisation_id")
    private String organisationId;
    
    @Column(name = "provider_id")
    private String providerId;
    
    @Column(name = "environment_id")
    private String environmentId;
    
    private String name;
    
    @Column(name = "account_id")
    private String accountId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type")
    private AccountAuthType authType;
    
    // In reality, token/role properties might be stored in a separate table or securely.
    // For this demonstration, we'll keep it simple but ensure they are never exposed in GET responses.
    private String token;
    
    @Column(name = "role_arn")
    private String roleArn;
    
    private String status;
    
    @Column(name = "created_at")
    private Instant createdAt;
    
    @Column(name = "updated_at")
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganisationId() { return organisationId; }
    public void setOrganisationId(String organisationId) { this.organisationId = organisationId; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getEnvironmentId() { return environmentId; }
    public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public AccountAuthType getAuthType() { return authType; }
    public void setAuthType(AccountAuthType authType) { this.authType = authType; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRoleArn() { return roleArn; }
    public void setRoleArn(String roleArn) { this.roleArn = roleArn; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
