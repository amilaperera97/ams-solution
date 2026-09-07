package com.example.certplatform.api.controller;

import com.example.certplatform.domain.enums.CloudProviderType;

public record CreateProviderRequest(
    String name,
    CloudProviderType type
) {}
