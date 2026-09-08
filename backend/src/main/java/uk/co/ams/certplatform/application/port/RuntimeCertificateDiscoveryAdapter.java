package com.example.certplatform.application.port;

import com.example.certplatform.domain.model.ApplicationMetadata;
import com.example.certplatform.domain.model.DiscoveryResult;
import com.example.certplatform.domain.model.ScanContext;

public interface RuntimeCertificateDiscoveryAdapter {
    
    boolean supports(ApplicationMetadata appMetadata);

    DiscoveryResult discover(ScanContext context, ApplicationMetadata appMetadata, String targetInstanceId);
}
