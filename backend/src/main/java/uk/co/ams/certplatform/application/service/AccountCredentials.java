package uk.co.ams.certplatform.application.service;

/**
 * Credentials supplied when an account is registered. Secrets are held here in
 * plaintext only long enough to be validated and handed to the repository, which
 * encrypts them before they reach the database.
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

    public boolean hasAccessKey() {
        return isPresent(accessKeyId) && isPresent(secretAccessKey);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
