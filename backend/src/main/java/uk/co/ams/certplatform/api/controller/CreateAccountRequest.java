package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.application.service.AccountCredentials;
import uk.co.ams.certplatform.domain.enums.AccountAuthType;

public record CreateAccountRequest(
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
