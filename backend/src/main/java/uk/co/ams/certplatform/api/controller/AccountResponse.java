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
 *
 * The *Configured booleans let the edit form say "already set - leave blank to
 * keep" for values it is deliberately not shown.
 */
public record AccountResponse(
        String id,
        String organisationId,
        String providerId,
        String environmentId,
        String name,
        String accountId,
        AccountAuthType authType,
        String region,
        String accessKeyId,
        boolean credentialsConfigured,
        boolean roleArnConfigured,
        boolean externalIdConfigured,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static AccountResponse from(Account account) {
        boolean roleArnConfigured = isPresent(account.roleArn());
        boolean credentialsConfigured = isPresent(account.token())
                || roleArnConfigured
                || (isPresent(account.accessKeyId()) && isPresent(account.secretAccessKey()));

        return new AccountResponse(
                account.id(),
                account.organisationId(),
                account.providerId(),
                account.environmentId(),
                account.name(),
                account.accountId(),
                account.authType(),
                account.region(),
                SecretMasker.mask(account.accessKeyId()),
                credentialsConfigured,
                roleArnConfigured,
                isPresent(account.externalId()),
                account.status(),
                account.createdAt(),
                account.updatedAt());
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
