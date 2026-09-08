package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.domain.enums.ScanScopeType;
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
