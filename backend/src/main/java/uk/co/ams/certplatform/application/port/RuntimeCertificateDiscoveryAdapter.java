package uk.co.ams.certplatform.application.port;

import uk.co.ams.certplatform.domain.model.ApplicationMetadata;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;

public interface RuntimeCertificateDiscoveryAdapter {
    
    boolean supports(ApplicationMetadata appMetadata);

    DiscoveryResult discover(ScanContext context, ApplicationMetadata appMetadata, String targetInstanceId);
}
