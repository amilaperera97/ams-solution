package com.example.certplatform.api.controller;

import com.example.certplatform.domain.enums.AccountAuthType;
import com.example.certplatform.domain.model.Account;
import java.time.Instant;

public class AccountResponse {
    private String id;
    private String organisationId;
    private String providerId;
    private String environmentId;
    private String name;
    private String accountId;
    private AccountAuthType authType;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public AccountResponse() {}

    public AccountResponse(Account account) {
        this.id = account.getId();
        this.organisationId = account.getOrganisationId();
        this.providerId = account.getProviderId();
        this.environmentId = account.getEnvironmentId();
        this.name = account.getName();
        this.accountId = account.getAccountId();
        this.authType = account.getAuthType();
        this.status = account.getStatus();
        this.createdAt = account.getCreatedAt();
        this.updatedAt = account.getUpdatedAt();
    }

    public String getId() { return id; }
    public String getOrganisationId() { return organisationId; }
    public String getProviderId() { return providerId; }
    public String getEnvironmentId() { return environmentId; }
    public String getName() { return name; }
    public String getAccountId() { return accountId; }
    public AccountAuthType getAuthType() { return authType; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
