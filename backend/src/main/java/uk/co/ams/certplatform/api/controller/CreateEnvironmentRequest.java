package uk.co.ams.certplatform.api.controller;

public record CreateEnvironmentRequest(
    String name,
    String description
) {}
