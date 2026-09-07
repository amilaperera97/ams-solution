package com.example.certplatform.application.port;

import com.example.certplatform.domain.model.DiscoveryResult;
import com.example.certplatform.domain.model.ScanContext;
import com.example.certplatform.domain.model.OperatingSystemMetadata;

public interface OperatingSystemCertificateDiscoveryStrategy {

    boolean supports(OperatingSystemMetadata osMetadata);

    DiscoveryResult discover(ScanContext context, OperatingSystemMetadata osMetadata, String targetInstanceId);
}
