package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;

import java.time.Instant;

/**
 * A cloud account the platform is allowed to scan, together with the credentials
 * it authenticates with.
 *
 * <p>Only the fields for the declared {@link #authType} are populated; the rest
 * stay null. Secret material is held here in plaintext but only ever in memory -
 * the repository adapter encrypts it on the way to the database and
 * {@code AccountResponse} keeps it out of API responses.
 *
 * @param id              platform identifier
 * @param organisationId  owning organisation
 * @param providerId      provider this account belongs to
 * @param environmentId   environment this account belongs to
 * @param name            human-readable name
 * @param accountId       the provider's own account identifier
 * @param authType        which of the credential sets below is in use
 * @param token           TOKEN auth
 * @param roleArn         IAM_ROLE auth
 * @param externalId      optional sts:ExternalId for IAM_ROLE auth
 * @param accessKeyId     ACCESS_KEY auth
 * @param secretAccessKey ACCESS_KEY auth
 * @param region          home region used for provider API calls
 * @param status          lifecycle status
 * @param createdAt       when the account was registered
 * @param updatedAt       when it was last changed
 */
public record Account(
        String id,
        String organisationId,
        String providerId,
        String environmentId,
        String name,
        String accountId,
        AccountAuthType authType,
        String token,
        String roleArn,
        String externalId,
        String accessKeyId,
        String secretAccessKey,
        String region,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public Account withId(String id) {
        return toBuilder().id(id).build();
    }

    public Account withStatus(String status) {
        return toBuilder().status(status).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Starting point for a modified copy - the stand-in for the setters this used to have. */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .organisationId(organisationId)
                .providerId(providerId)
                .environmentId(environmentId)
                .name(name)
                .accountId(accountId)
                .authType(authType)
                .token(token)
                .roleArn(roleArn)
                .externalId(externalId)
                .accessKeyId(accessKeyId)
                .secretAccessKey(secretAccessKey)
                .region(region)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt);
    }

    public static final class Builder {
        private String id;
        private String organisationId;
        private String providerId;
        private String environmentId;
        private String name;
        private String accountId;
        private AccountAuthType authType;
        private String token;
        private String roleArn;
        private String externalId;
        private String accessKeyId;
        private String secretAccessKey;
        private String region;
        private String status;
        private Instant createdAt;
        private Instant updatedAt;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder organisationId(String organisationId) { this.organisationId = organisationId; return this; }
        public Builder providerId(String providerId) { this.providerId = providerId; return this; }
        public Builder environmentId(String environmentId) { this.environmentId = environmentId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder accountId(String accountId) { this.accountId = accountId; return this; }
        public Builder authType(AccountAuthType authType) { this.authType = authType; return this; }
        public Builder token(String token) { this.token = token; return this; }
        public Builder roleArn(String roleArn) { this.roleArn = roleArn; return this; }
        public Builder externalId(String externalId) { this.externalId = externalId; return this; }
        public Builder accessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; return this; }
        public Builder secretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; return this; }
        public Builder region(String region) { this.region = region; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Account build() {
            return new Account(id, organisationId, providerId, environmentId, name, accountId, authType,
                    token, roleArn, externalId, accessKeyId, secretAccessKey, region, status,
                    createdAt, updatedAt);
        }
    }
}
