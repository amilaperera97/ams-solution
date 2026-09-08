package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.shared.security.SecretMasker;
import java.time.Instant;

/**
 * Outbound view of an account. Credentials are never included - neither the
 * secrets (token, secret access key) nor the role ARN, which AccountIntegrationTest
 * pins as non-exposed. The access key id is masked to its last four characters so
 * an operator can tell which key is configured without it being readable.
 */
public class AccountResponse {
    private String id;
    private String organisationId;
    private String providerId;
    private String environmentId;
    private String name;
    private String accountId;
    private AccountAuthType authType;
    private String region;
    private String accessKeyId;
    private boolean credentialsConfigured;
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
        this.region = account.getRegion();
        this.accessKeyId = SecretMasker.mask(account.getAccessKeyId());
        this.credentialsConfigured = isPresent(account.getToken())
                || isPresent(account.getRoleArn())
                || (isPresent(account.getAccessKeyId()) && isPresent(account.getSecretAccessKey()));
        this.status = account.getStatus();
        this.createdAt = account.getCreatedAt();
        this.updatedAt = account.getUpdatedAt();
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    public String getId() { return id; }
    public String getOrganisationId() { return organisationId; }
    public String getProviderId() { return providerId; }
    public String getEnvironmentId() { return environmentId; }
    public String getName() { return name; }
    public String getAccountId() { return accountId; }
    public AccountAuthType getAuthType() { return authType; }
    public String getRegion() { return region; }
    public String getAccessKeyId() { return accessKeyId; }
    public boolean isCredentialsConfigured() { return credentialsConfigured; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
