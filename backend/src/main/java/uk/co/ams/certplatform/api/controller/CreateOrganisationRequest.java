package uk.co.ams.certplatform.api.controller;

public record CreateOrganisationRequest(
    String name,
    String description
) {}
