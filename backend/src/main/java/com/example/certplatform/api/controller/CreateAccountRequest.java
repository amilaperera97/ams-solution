package com.example.certplatform.api.controller;

import com.example.certplatform.domain.enums.AccountAuthType;

public record CreateAccountRequest(
    String name,
    String accountId,
    AccountAuthType authType,
    String token,
    String roleArn
) {}
