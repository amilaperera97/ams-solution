package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;

public record CreateProviderRequest(
    String name,
    CloudProviderType type
) {}
