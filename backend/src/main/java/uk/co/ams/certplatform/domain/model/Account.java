package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;
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
    private String externalId; // Optional sts:ExternalId for IAM_ROLE auth
    private String accessKeyId; // For ACCESS_KEY auth
    private String secretAccessKey; // For ACCESS_KEY auth - held in plaintext in memory only
    private String region; // Home region used for provider API calls
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

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
