package com.example.certplatform.application.port;

import com.example.certplatform.domain.model.DiscoveryResult;
import com.example.certplatform.domain.model.ScanContext;

public interface CertificateDiscoveryStrategy {

    DiscoveryCapability capability();

    boolean supports(ScanContext context);

    DiscoveryResult discover(ScanContext context);
}
