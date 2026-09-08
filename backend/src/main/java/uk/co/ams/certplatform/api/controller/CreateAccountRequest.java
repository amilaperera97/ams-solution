package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;

public record CreateAccountRequest(
    String name,
    String accountId,
    AccountAuthType authType,
    String token,
    String roleArn
) {}
