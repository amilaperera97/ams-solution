package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.application.service.AccountCredentials;
import uk.co.ams.certplatform.domain.enums.AccountAuthType;

/**
 * Partial update of an account. Every field is optional: null or blank means
 * "leave what is stored alone". That is what lets the edit form save a change
 * without resending secrets it was never given - see {@link AccountResponse},
 * which returns no token, role ARN or secret access key.
 */
public record UpdateAccountRequest(
    String name,
    String accountId,
    AccountAuthType authType,
    String token,
    String roleArn,
    String externalId,
    String accessKeyId,
    String secretAccessKey,
    String region
) {
    public AccountCredentials toCredentials() {
        return new AccountCredentials(token, roleArn, externalId, accessKeyId, secretAccessKey, region);
    }
}
