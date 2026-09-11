package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.domain.model.Account;

/**
 * Credentials supplied when an account is registered or updated. Secrets are held
 * here in plaintext only long enough to be validated and handed to the repository,
 * which encrypts them before they reach the database.
 */
public record AccountCredentials(
    String token,
    String roleArn,
    String externalId,
    String accessKeyId,
    String secretAccessKey,
    String region
) {
    public static AccountCredentials none() {
        return new AccountCredentials(null, null, null, null, null, null);
    }

    /** The credentials already stored against an account. */
    public static AccountCredentials of(Account account) {
        return new AccountCredentials(account.token(), account.roleArn(), account.externalId(),
                account.accessKeyId(), account.secretAccessKey(), account.region());
    }

    public boolean hasAccessKey() {
        return isPresent(accessKeyId) && isPresent(secretAccessKey);
    }

    /** Whitespace-only values are the same as absent, and stored values never keep padding. */
    public AccountCredentials trimmed() {
        return new AccountCredentials(trimToNull(token), trimToNull(roleArn), trimToNull(externalId),
                trimToNull(accessKeyId), trimToNull(secretAccessKey), trimToNull(region));
    }

    /**
     * Updates only replace what the caller actually sent. A field left out (or sent
     * blank) keeps the stored value, which is how an edit form can update a role ARN
     * without the client ever having seen the secret access key it is preserving.
     */
    public AccountCredentials mergedOver(AccountCredentials existing) {
        return new AccountCredentials(
            pick(token, existing.token()),
            pick(roleArn, existing.roleArn()),
            pick(externalId, existing.externalId()),
            pick(accessKeyId, existing.accessKeyId()),
            pick(secretAccessKey, existing.secretAccessKey()),
            pick(region, existing.region()));
    }

    private static String pick(String supplied, String existing) {
        return isPresent(supplied) ? supplied : existing;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return isPresent(value) ? value.trim() : null;
    }
}
