package com.example.certplatform.api.controller;

public record CreateEnvironmentRequest(
    String name,
    String description
) {}
