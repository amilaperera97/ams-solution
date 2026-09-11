package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;

/**
 * Outcome of checking that an account's credentials still work.
 *
 * @param status    {@value #CONNECTED} or {@value #FAILED}
 * @param provider  cloud the credentials were tested against
 * @param accountId account the credentials resolved to - for a real STS call this
 *                  is the account the caller actually is, which is worth seeing
 *                  when it differs from the one that was configured
 * @param message   human-readable detail, always populated
 */
public record ConnectionTestResult(String status, CloudProviderType provider, String accountId, String message) {

    public static final String CONNECTED = "CONNECTED";
    public static final String FAILED = "FAILED";

    public static ConnectionTestResult connected(CloudProviderType provider, String accountId, String message) {
        return new ConnectionTestResult(CONNECTED, provider, accountId, message);
    }

    public static ConnectionTestResult failed(CloudProviderType provider, String accountId, String message) {
        return new ConnectionTestResult(FAILED, provider, accountId, message);
    }
}
