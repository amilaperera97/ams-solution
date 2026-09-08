package com.example.certplatform.domain.model;

import com.example.certplatform.domain.enums.AccountAuthType;
import java.time.Instant;

public class Account {
    private String id;
    private String organisationId;
    private String providerId;
    private String environmentId;
    private String name;
    private String accountId;
    private AccountAuthType authType;
    private String token; // For TOKEN auth
    private String roleArn; // For IAM_ROLE auth
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public Account() {}

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
