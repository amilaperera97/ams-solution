package com.example.certplatform.api.controller;

import com.example.certplatform.domain.enums.ScanScopeType;
import java.util.List;

public record CreateScanRequest(
    String name,
    ScanScopeType scopeType,
    List<String> providerIds,
    List<String> environmentIds,
    List<String> accountIds,
    List<String> regions,
    List<String> services
) {}
