package uk.co.ams.certplatform.infrastructure.persistence.entity;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;
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
    
    // Secret-bearing columns (token, secret_access_key) are written encrypted when a
    // secret key is configured, and are never exposed in GET responses.
    private String token;
    
    @Column(name = "role_arn")
    private String roleArn;
    
    @Column(name = "external_id")
    private String externalId;
    
    @Column(name = "access_key_id")
    private String accessKeyId;
    
    @Column(name = "secret_access_key")
    private String secretAccessKey;
    
    private String region;
    
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
